package com.octo4a.camera

//import com.octo4a.camera.cameraWithId

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.renderscript.*
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.octo4a.repository.LoggerRepository
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.utils.preferences.MainPreferences
import org.koin.android.ext.android.inject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraService : LifecycleService(), MJpegFrameProvider {
    private var latestFrame: ByteArray = ByteArray(0)
    private var listenerCount = 0

    private val cameraSettings: MainPreferences by inject()
    private val logger: LoggerRepository by inject()
    private val octoprintHandler: OctoPrintHandlerRepository by inject()
    private val cameraEnumerationRepository: CameraEnumerationRepository by inject()
    private val captureExecutor by lazy { Executors.newCachedThreadPool() }
    private val nativeUtils by lazy { NativeCameraUtils() }
    var currentCamera: Camera? = null

    var fpsLimit = -1
    var lastImageMilliseconds = System.currentTimeMillis()

    var cameraInitialized = false
    var cameraProcessProvider: ProcessCameraProvider? = null

    fun getCurrentRotation(): Int {
        val currentRotation = cameraSettings.imageRotation?.toIntOrNull() ?: 0
        return when (currentRotation) {
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }
    }

    private val cameraSelector by lazy {
        CameraSelector.Builder().apply {
            requireLensFacing(
                cameraEnumerationRepository.cameraWithId(cameraSettings.selectedCamera!!)?.lensFacing
                    ?: CameraSelector.LENS_FACING_FRONT
            )
        }.build()
    }
    private val cameraPreview by lazy {
        Preview.Builder()
            .setTargetResolution(Size.parseSize(cameraSettings.selectedResolution ?: "1280x720"))
            .setTargetRotation(getCurrentRotation())
            .build()
    }

    private val imageCapture by lazy {
        ImageCapture.Builder()
            .setTargetResolution(Size.parseSize(cameraSettings.selectedResolution ?: "1280x720"))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(getCurrentRotation())
            .build()
    }

    private val imageAnalysis by lazy {
        ImageAnalysis.Builder()
            .setTargetResolution(Size.parseSize(cameraSettings.selectedResolution ?: "1280x720"))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    override val newestFrame: ByteArray
        get() = synchronized(latestFrame) { return latestFrame }

    inner class LocalBinder : Binder() {
        fun getService(): CameraService = this@CameraService
    }

    private val binder = LocalBinder()

    override suspend fun takeSnapshot(): ByteArray = suspendCoroutine {
        if (!cameraInitialized) {
            it.resume(ByteArray(0))
        } else {
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.capacity()).also { array -> buffer.get(array) }
                        super.onCaptureSuccess(image) //Closes the image
                        image.close()
                        it.resume(bytes)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        super.onError(exception)
                        logger.log(this) { "Single capture error: $exception" }
                        it.resume(ByteArray(0))
                    }
                })
        }
    }

    override fun registerListener() {
        synchronized(listenerCount) {
            listenerCount++
        }
        logger.log(this) { "Camera server register listener" }

        val turnFlashOn = cameraSettings.flashWhenObserved

        if (listenerCount > 0  && turnFlashOn && cameraInitialized) {
            currentCamera?.cameraControl?.enableTorch(true)
        }
    }

    override fun unregisterListener() {
        synchronized(listenerCount) {
            listenerCount--
        }
        logger.log(this) { "Camera server unregister listener" }

        if (listenerCount < 1 && cameraInitialized) {
            currentCamera?.cameraControl?.enableTorch(false)
        }
    }

    private val mjpegServer by lazy { MJpegServer(5001, this) }

    private val callbackExecutorPool = Executors.newCachedThreadPool()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Thread {
            mjpegServer.stopServer()
        }.start()
        octoprintHandler.isCameraServerRunning = false
    }

    fun getPreview(): Preview {
        cameraInitialized = false
        val turnFlashOn = cameraSettings.flashWhenObserved

        cameraProcessProvider?.unbindAll()
        val camera = cameraProcessProvider?.bindToLifecycle(
            this,
            cameraSelector,
            imageCapture,
            imageAnalysis,
            cameraPreview
        )

        if (turnFlashOn) {
            camera?.cameraControl?.enableTorch(true)
        }
        cameraInitialized = true
        return cameraPreview
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        fpsLimit = cameraSettings.fpsLimit?.toIntOrNull() ?: 0
        cameraPreview.targetRotation = getCurrentRotation()

        readyCamera()
        Thread {
            mjpegServer.startServer()
        }.start()
        return START_STICKY
    }


    @SuppressWarnings("UnsafeExperimentalUsageError")
    private fun readyCamera() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
        val out = ByteArrayOutputStream()

        val rotation = cameraSettings.imageRotation?.toIntOrNull() ?: 0

        cameraProviderFuture.addListener({
            cameraProcessProvider = cameraProviderFuture.get()


            imageAnalysis.setAnalyzer(callbackExecutorPool) { image ->
                // Roughly limit fps to user's chosen value
                if (fpsLimit > 0) {
                    val timeDiff = System.currentTimeMillis() - lastImageMilliseconds
                    val requiredWait = 1000 / fpsLimit
                    if (timeDiff < requiredWait) {
                        Thread.sleep(requiredWait - timeDiff)
                    }
                }

                synchronized(listenerCount) {
                    if (listenerCount > 0 || latestFrame.isEmpty()) {
                        var nv21 = nativeUtils.toNv21(image)!!

                        if (rotation > 0) {
                            nv21 = RotateUtils.rotateNV21(nv21, image.width, image.height, rotation)!!
                        }

                        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)

                        synchronized(latestFrame) {
                            latestFrame = out.toByteArray()
                            lastImageMilliseconds = System.currentTimeMillis()
                            out.reset()
                        }
                    }
                    image.close()
                }
            }

            cameraProcessProvider?.unbindAll()
            currentCamera = cameraProcessProvider?.bindToLifecycle(
                this,
                cameraSelector,
                imageCapture,
                imageAnalysis
            )

            if (cameraSettings.disableAF) {
                val cameraControl: CameraControl = currentCamera!!.cameraControl
                val camera2CameraControl: Camera2CameraControl =
                    Camera2CameraControl.from(cameraControl)

                val captureRequestOptions = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_OFF
                    )
                    .build()
                camera2CameraControl.captureRequestOptions = captureRequestOptions
            }
            cameraInitialized = true
        }, ContextCompat.getMainExecutor(applicationContext))
        octoprintHandler.isCameraServerRunning = true
    }
}