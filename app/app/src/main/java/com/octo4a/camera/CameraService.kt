package com.octo4a.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.renderscript.*
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.octo4a.repository.LoggerRepository
//import com.octo4a.camera.cameraWithId
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.utils.NV21toJPEG
import com.octo4a.utils.YUV420toNV21
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
    }

    override fun unregisterListener() {
        synchronized(listenerCount) {
            listenerCount--
        }
        logger.log(this) { "Camera server unregister listener" }
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

    val renderScript by lazy { RenderScript.create(applicationContext) }

    private fun nv21ToBitmap(yuvByteArray: ByteArray, width: Int, height: Int): Bitmap {

        val yuvToRgbIntrinsic =
            ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript))

        val yuvType = Type.Builder(renderScript, Element.U8(renderScript)).setX(yuvByteArray.size)
        val allocationIn =
            Allocation.createTyped(renderScript, yuvType.create(), Allocation.USAGE_SCRIPT)

        val rgbaType =
            Type.Builder(renderScript, Element.RGBA_8888(renderScript)).setX(width).setY(height)
        val allocationOut =
            Allocation.createTyped(renderScript, rgbaType.create(), Allocation.USAGE_SCRIPT)

        allocationIn.copyFrom(yuvByteArray)

        yuvToRgbIntrinsic.setInput(allocationIn)
        yuvToRgbIntrinsic.forEach(allocationOut)

        val bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        allocationOut.copyTo(bmpout)

        allocationOut.destroy()
        allocationIn.destroy()
        yuvToRgbIntrinsic.destroy()
        return bmpout
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

        cameraProviderFuture.addListener({
            cameraProcessProvider = cameraProviderFuture.get()

            val turnFlashOn = cameraSettings.flashWhenObserved

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
                        val bitmap = nv21ToBitmap(image.YUV420toNV21(), image.width, image.height)


                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)

                        synchronized(latestFrame) {
                            latestFrame = out.toByteArray()
                            lastImageMilliseconds = System.currentTimeMillis()
                            bitmap.recycle()
                            out.reset()
                        }
                    }
                    image.close()
                }
            }

            cameraProcessProvider?.unbindAll()
            val camera = cameraProcessProvider?.bindToLifecycle(
                this,
                cameraSelector,
                imageCapture,
                imageAnalysis
            )

            if (turnFlashOn) {
                camera?.cameraControl?.enableTorch(true)
            }
            cameraInitialized = true
        }, ContextCompat.getMainExecutor(applicationContext))
        octoprintHandler.isCameraServerRunning = true
    }
}