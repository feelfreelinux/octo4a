package com.octo4a.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.OnLifecycleEvent
import com.octo4a.repository.LoggerRepository
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.utils.CancelableTimer
import com.octo4a.utils.WaitableEvent
import com.octo4a.utils.preferences.MainPreferences
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.HashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.koin.android.ext.android.inject

const val UNBIND_DELAY_MS: Long = 5 * 60 * 1000 // Unbind the camera after 5 minutes of no use
const val UNBIND_STREAM_DELAY_MS: Long = 10 * 1000 // Unbind streams faster to save CPU
const val JPEG_QUALITY: Int = 70

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraService : LifecycleService(), MJpegFrameProvider {

  enum class InitState {
    NOT_INITIALIZED,
    INITIALIZING,
    INITIALIZED,
    UNINITIALIZING,
    FAILED
  }

  data class CompletableInitState(
      var state: InitState,
      val callback: ((CompletableInitState) -> Unit)? = null,
      val unbindDelayMs: Long = UNBIND_DELAY_MS,
      var refcnt: Int = 0,
      var waitEvent: WaitableEvent = WaitableEvent(),
      val unbindTimer: CancelableTimer = CancelableTimer(),
      var cameraControl: CameraControl? = null,
  )

  fun CompletableInitState.setState(newState: InitState) {
    state = newState
    callback?.invoke(this)
  }

  data class LatestFrameInfo(
      val waitEvent: WaitableEvent = WaitableEvent(),
      var frameInfo: MJpegFrameProvider.FrameInfo
  )

  private val _cameraSettings: MainPreferences by inject()
  private val _logger: LoggerRepository by inject()

  private val _latestFrameInfo: LatestFrameInfo =
      LatestFrameInfo(frameInfo = MJpegFrameProvider.FrameInfo(id = 1))

  private var _cameraProcessProvider: ProcessCameraProvider? = null
  private val _cameraBoundUseCases: MutableMap<UseCase, CompletableInitState> = HashMap()
  private var _fpsLimit: Int = -1
  private var _torchRefCnt: Int = 0

  private val _octoprintHandler: OctoPrintHandlerRepository by inject()
  private val _cameraEnumerationRepository: CameraEnumerationRepository by inject()
  private val _captureExecutor by lazy { Executors.newCachedThreadPool() }
  private val nativeUtils by lazy { NativeCameraUtils() }

  private val _mjpegServer by lazy { MJpegServer(5001, this) }
  private val _callbackExecutorPool by lazy { Executors.newCachedThreadPool() }
  private var _lastImageMilliseconds = System.currentTimeMillis()

  private fun <T> setFpsAndAutofocus(builder: T) where T : ExtendableBuilder<*>, T : Any {
    val ext: Camera2Interop.Extender<*> = Camera2Interop.Extender(builder)
    if (_cameraSettings.disableAF) {
      ext.setCaptureRequestOption(
          CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
      ext.setCaptureRequestOption(
          CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
      ext.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
    } else {
      ext.setCaptureRequestOption(
          CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
    }

    if (_fpsLimit > 0) {
      ext.setCaptureRequestOption(
          CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range<Int>(_fpsLimit, _fpsLimit))
    }
  }

  private val _cameraSelector by lazy {
    CameraSelector.Builder()
        .apply {
          requireLensFacing(
              _cameraEnumerationRepository
                  .cameraWithId(_cameraSettings.selectedCamera!!)
                  ?.lensFacing ?: CameraSelector.LENS_FACING_FRONT)
        }
        .build()
  }

  private val _cameraPreview by lazy {
    val builder =
        Preview.Builder()
            .setTargetResolution(
                Size.parseSize(_cameraSettings.selectedVideoResolution ?: "1280x720"))
            .setTargetRotation(getSettingsRotation())

    setFpsAndAutofocus(builder)

    val ret = builder.build()
    _cameraBoundUseCases[ret] =
        CompletableInitState(
            InitState.NOT_INITIALIZED,
            callback = ::torchControlCallback,
            unbindDelayMs = UNBIND_STREAM_DELAY_MS)

    ret
  }

  private val _imageCapture by lazy {
    val builder =
        ImageCapture.Builder()
            .setTargetResolution(Size.parseSize(_cameraSettings.selectedResolution ?: "1280x720"))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(getSettingsRotation())
            .setFlashMode(
                if (_cameraSettings.flashWhenObserved) ImageCapture.FLASH_MODE_ON
                else ImageCapture.FLASH_MODE_OFF)
    val ret = builder.build()
    ret
  }

  private val _imageAnalysis by lazy {
    val builder =
        ImageAnalysis.Builder()
            .setTargetResolution(
                Size.parseSize(_cameraSettings.selectedVideoResolution ?: "1280x720"))
            .setTargetRotation(getSettingsRotation())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

    setFpsAndAutofocus(builder)

    val ret = builder.build()
    _cameraBoundUseCases[ret] =
        CompletableInitState(
            InitState.NOT_INITIALIZED,
            callback = ::torchControlCallback,
            unbindDelayMs = UNBIND_STREAM_DELAY_MS)
    ret.setAnalyzer(_callbackExecutorPool, ::analyzeFrame)

    ret
  }

  private fun getCameraControl(): CameraControl? {
    var cameraControl: CameraControl? = null
    _cameraBoundUseCases.entries
        .firstOrNull { it.value.cameraControl != null }
        ?.let { entry -> cameraControl = entry.value.cameraControl }
    _logger.log(this) { "Got camera control : $cameraControl" }
    return cameraControl
  }

  private fun addTorchUser() {
    synchronized(_torchRefCnt) {
      _torchRefCnt += 1
      if (_torchRefCnt == 1) {
        if (_cameraSettings.flashWhenObserved) {
          getCameraControl()?.enableTorch(true)
        }
        _logger.log(this) { "Torch now has users" }
      }
    }
  }

  private fun removeTorchUser() {
    synchronized(_torchRefCnt) {
      if (_torchRefCnt > 0) {
        _torchRefCnt -= 1
      } else {
        _logger.log(this) { "Excessive removeTorchUser calls!" }
      }
      if (_torchRefCnt == 0) {
        if (_cameraSettings.flashWhenObserved) {
          getCameraControl()?.enableTorch(false)
        }
        _logger.log(this) { "Torch has no more users" }
      }
    }
  }

  private fun torchControlCallback(initState: CompletableInitState): Unit {
    when (initState.state) {
      InitState.INITIALIZED -> {
        addTorchUser()
      }
      InitState.UNINITIALIZING -> {
        removeTorchUser()
      }
      else -> {}
    }
  }

  private fun getSettingsRotation(): Int {
    val currentRotation = _cameraSettings.imageRotation?.toIntOrNull() ?: -1
    return when (currentRotation) {
      90 -> Surface.ROTATION_90
      180 -> Surface.ROTATION_180
      270 -> Surface.ROTATION_270
      else -> Surface.ROTATION_0
    }
  }

  private fun getBestAvailFps(): Int {
    val targetFps = _cameraSettings.fpsLimit?.toIntOrNull() ?: -1
    val availableFps =
        _cameraEnumerationRepository.cameraWithId(_cameraSettings.selectedCamera!!)?.frameRates
    val bestFps = availableFps?.firstOrNull { it >= targetFps } ?: -1
    return bestFps
  }

  private fun toBitmap(buffer: ByteBuffer, rotation: Float = 0f): Bitmap {
    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
    var matrix: Matrix? = null
    if (rotation != 0f) {
      matrix = Matrix().apply { postRotate(rotation) }
    }
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
  }

  private fun compressNv21(
      nv21: ByteArray,
      width: Int,
      height: Int,
      quality: Int = JPEG_QUALITY
  ): ByteArray {
    val out = ByteArrayOutputStream()
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
    return out.toByteArray()
  }

  private fun setNextFrame(image: ByteArray) {
    synchronized(_latestFrameInfo) {
      _latestFrameInfo.frameInfo =
          _latestFrameInfo.frameInfo.copy(image = image, id = _latestFrameInfo.frameInfo.id + 1)
    }
    _latestFrameInfo.waitEvent.set()
  }

  override fun getNewFrame(prevFrame: MJpegFrameProvider.FrameInfo?): MJpegFrameProvider.FrameInfo {
    while (_latestFrameInfo.frameInfo.id <= prevFrame?.id ?: 0) {
      _latestFrameInfo.waitEvent.wait(autoreset = true)
    }
    synchronized(_latestFrameInfo) {
      return _latestFrameInfo.frameInfo.copy()
    }
  }

  inner class LocalBinder : Binder() {
    fun getService(): CameraService = this@CameraService
  }

  private val binder = LocalBinder()

  override suspend fun takeSnapshot(): ByteArray = suspendCoroutine {
    _logger.log(this) { "Received takeSnapshot request" }
    if (!initUseCase(_imageCapture, block = true)) {
      it.resume(ByteArray(0))
      return@suspendCoroutine
    }
    _imageCapture.takePicture(
        _captureExecutor,
        object : ImageCapture.OnImageCapturedCallback() {
          override fun onCaptureSuccess(image: ImageProxy) {
            _logger.log(this) { "Snapshot Capture Success" }
            val bitmap =
                toBitmap(
                    image.planes[0].buffer, image.getImageInfo().getRotationDegrees().toFloat())
            val compressedStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, compressedStream)
            it.resume(compressedStream.toByteArray())
            super.onCaptureSuccess(image)
            image.close()
            deinitUseCase(_imageCapture)
          }

          override fun onError(exception: ImageCaptureException) {
            _logger.log(this) { "Failed to capture image: $exception" }
            deinitUseCase(_imageCapture)
            it.resume(ByteArray(0))
            super.onError(exception)
          }
        })
  }

  private fun sleepToLimitFps() {
    // Roughly limit fps to user's chosen value
    if (_fpsLimit > 0) {
      val timeDiff = System.currentTimeMillis() - _lastImageMilliseconds
      _lastImageMilliseconds = System.currentTimeMillis()
      val targetFrameTime = 1000 / _fpsLimit
      if (timeDiff < targetFrameTime) {
        Thread.sleep(targetFrameTime - timeDiff)
      }
    }
  }

  private fun analyzeFrame(image: ImageProxy) {
    val isI420 = (image.planes[1].pixelStride == 1)
    var nv21: ByteArray =
        if (isI420) nativeUtils.yuvToNv21Slow(image) else nativeUtils.toNv21(image)!!
    var realWidth = image.width
    var realHeight = image.height

    val rotation: Int = image.imageInfo.rotationDegrees
    if (rotation > 0) {
      nv21 = RotateUtils.rotate(nv21, realWidth, realHeight, rotation)!!
      if (rotation != 180) {
        realWidth = image.height
        realHeight = image.width
      }
    }
    setNextFrame(compressNv21(nv21, realWidth, realHeight))
    image.close()
    sleepToLimitFps()
  }

  override fun registerListener(): Boolean {
    _logger.log(this) { "Camera server register stream listener" }
    return initUseCase(_imageAnalysis, block = true)
  }

  override fun unregisterListener() {
    _logger.log(this) { "Camera server unregister stream listener" }
    deinitUseCase(_imageAnalysis)
  }

  override fun onBind(intent: Intent): IBinder {
    super.onBind(intent)
    return binder
  }

  override fun onDestroy() {
    super.onDestroy()
    Thread { _mjpegServer.stopServer() }.start()
    _octoprintHandler.isCameraServerRunning = false
  }

  fun getPreview(lifecycleOwner: LifecycleOwner): Preview? {
    if (!initUseCase(_cameraPreview, block = true)) {
      return null
    }

    // Bind to preview destruction via the passed in lifecycleowner
    lifecycleOwner
        .getLifecycle()
        .addObserver(
            object : LifecycleObserver {
              @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
              fun onStop() {
                _logger.log(this) { "Preview has stopped" }
                deinitUseCase(_cameraPreview)
                lifecycleOwner.lifecycle.removeObserver(this)
              }
            })
    return _cameraPreview
  }

  override fun onCreate() {
    super.onCreate()
    initCameraProvider()
  }

  @SuppressLint("UnsafeExperimentalUsageError")
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    _fpsLimit = _cameraSettings.fpsLimit?.toIntOrNull() ?: -1
    Thread { _mjpegServer.startServer() }.start()
    _octoprintHandler.isCameraServerRunning = true
    return START_STICKY
  }

  private fun initCameraProvider() {
    val isMainThread = Looper.getMainLooper().thread == Thread.currentThread()
    assert(isMainThread)
    if (_cameraProcessProvider != null) {
      return
    }

    if (!checkForCamera()) {
      return
    }

    val providerFuture = ProcessCameraProvider.getInstance(applicationContext)
    providerFuture.addListener(
        {
          try {
            _cameraProcessProvider = providerFuture.get()
            _cameraProcessProvider?.unbindAll()
            _logger.log(this) { "Camera initialized" }
          } catch (e: Exception) {
            _logger.log(this) { "Failed to bind to camera: $e" }
          }
        },
        ContextCompat.getMainExecutor(applicationContext))
  }

  private fun initUseCase(useCase: UseCase, block: Boolean = false): Boolean {
    if (_cameraProcessProvider == null) {
      _logger.log(this) { "Can't init use case $useCase, camera not initialized!" }
      return false
    }
    var waitForInitNeeded = false
    var initState: CompletableInitState
    synchronized(_cameraBoundUseCases) {
      _cameraBoundUseCases.computeIfAbsent(useCase) {
        CompletableInitState(InitState.NOT_INITIALIZED)
      }
      initState = _cameraBoundUseCases[useCase]!!
    }
    _logger.log(this) { "Entering initUseCase: [$useCase]  [$initState]" }
    synchronized(initState) {
      if (initState.state == InitState.UNINITIALIZING) {
        initState.unbindTimer.cancel()
        initState.setState(InitState.INITIALIZED)
      }
      if (initState.state == InitState.INITIALIZED) {
        initState.refcnt += 1
        return true
      }
      if (initState.state == InitState.INITIALIZING && block) {
        waitForInitNeeded = true
      }
      if (initState.state == InitState.NOT_INITIALIZED) {
        initState.setState(InitState.INITIALIZING)
      }
    }
    if (waitForInitNeeded) {
      initState.waitEvent.wait()
    }
    if (initState.state != InitState.INITIALIZING) {
      return (initState.state == InitState.INITIALIZED)
    }
    fun bindCamera() {
      var camera: Camera?
      try {
        camera = _cameraProcessProvider?.bindToLifecycle(this, _cameraSelector, useCase)

        synchronized(initState) {
          initState.refcnt += 1
          initState.cameraControl = camera?.cameraControl
          initState.setState(InitState.INITIALIZED)
        }
      } catch (e: Exception) {
        initState.setState(InitState.FAILED)
        _logger.log(this) { "Failed to bind camera: $e" }
      } finally {
        initState.waitEvent.set()
      }
    }
    val handler = Handler(Looper.getMainLooper())
    if (block) {
      val isMainThread = Looper.getMainLooper().thread == Thread.currentThread()
      if (isMainThread) {
        bindCamera()
      } else {
        /* Block while main loop binds */
        handler.post { bindCamera() }
        initState.waitEvent.wait()
      }
    } else {
      handler.post { bindCamera() }
    }
    return (initState.state == InitState.INITIALIZED ||
        (!block && initState.state == InitState.INITIALIZING))
  }

  private fun deinitUseCase(useCase: UseCase) {
    var initState: CompletableInitState
    synchronized(_cameraBoundUseCases) {
      _cameraBoundUseCases.computeIfAbsent(useCase) {
        CompletableInitState(InitState.NOT_INITIALIZED)
      }
      initState = _cameraBoundUseCases[useCase]!!
    }
    synchronized(initState) {
      _logger.log(this) { "Entering deinitUseCase [$useCase] : [$initState]" }
      assert(initState.refcnt > 0)
      initState.refcnt -= 1
      if (initState.refcnt == 0) {
        initState.setState(InitState.UNINITIALIZING)
        initState.unbindTimer.start(
            initState.unbindDelayMs,
            {
              synchronized(initState) {
                if (initState.state == InitState.UNINITIALIZING) {
                  _cameraProcessProvider?.unbind(useCase)
                  initState.cameraControl = null
                  initState.setState(InitState.NOT_INITIALIZED)
                  initState.waitEvent.reset()

                  _logger.log(this) {
                    "Unbound use case $useCase after unreferenced for ${initState.unbindDelayMs} ms"
                  }
                }
              }
            })
      }
    }
  }

  private fun checkForCamera(): Boolean {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
        PackageManager.PERMISSION_GRANTED) {
      return false
    }
    // ensure that the cameras are enumerated
    _cameraEnumerationRepository.enumerateCameras()
    // check if the device has any cameras at all
    // some TV boxes have no cameras at all, and it causes cameraSelector to throw an exception
    if (_cameraEnumerationRepository.enumeratedCameras.value?.isEmpty() != false) {
      return false
    }
    return true
  }
}
