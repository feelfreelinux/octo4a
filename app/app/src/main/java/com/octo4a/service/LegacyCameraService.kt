package com.octo4a.service

//import com.octo4a.camera.cameraWithId

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Binder
import android.os.IBinder
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleService
import com.octo4a.camera.MJpegFrameProvider
import com.octo4a.camera.MJpegServer
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.utils.log
import com.octo4a.utils.preferences.MainPreferences
import org.koin.android.ext.android.inject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.suspendCoroutine


class LegacyCameraService : LifecycleService(), MJpegFrameProvider, SurfaceHolder.Callback {
    private var latestFrame: ByteArray = ByteArray(0)
    private var listenerCount = 0
    private val preview by lazy {
        SurfaceView(applicationContext)
    }
    private val camera by lazy {
        Camera.open()
    }
    private val cameraSettings: MainPreferences by inject()
    private val octoprintHandler: OctoPrintHandlerRepository by inject()


    override val newestFrame: ByteArray
        get() = synchronized(latestFrame) { return latestFrame }

    inner class LocalBinder : Binder() {
        fun getService(): LegacyCameraService = this@LegacyCameraService
    }

    private val windowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private val binder = LocalBinder()

    override suspend fun takeSnapshot(): ByteArray = suspendCoroutine {
//        if (!cameraInitialized) {
//            it.resume(ByteArray(0))
//        } else {
//            imageCapture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
//                override fun onCaptureSuccess(image: ImageProxy) {
//                    val buffer = image.planes[0].buffer
//                    val bytes = ByteArray(buffer.capacity()).also { array -> buffer.get(array) }
//                    super.onCaptureSuccess(image) //Closes the image
//                    image.close()
//                    it.resume(bytes)
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                    super.onError(exception)
//                    log { "Single capture error: $exception" }
//                    it.resume(ByteArray(0))
//                }
//            })
//        }
    }

    override fun registerListener() {
        synchronized(listenerCount) {
            listenerCount++
        }
        log { "REGISTER" }
    }

    override fun unregisterListener() {
        synchronized(listenerCount) {
            listenerCount--
        }
        log { "UNREGISTER" }
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

//    fun getPreview(): Preview {
//        cameraInitialized = false
//        val turnFlashOn = cameraSettings.flashWhenObserved
//
//        cameraProcessProvider?.unbindAll()
//        val camera = cameraProcessProvider?.bindToLifecycle(
//            this,
//            cameraSelector,
//            imageCapture,
//            imageAnalysis,
//            cameraPreview
//        )
//
//        if (turnFlashOn) {
//            camera?.cameraControl?.enableTorch(true)
//        }
//        cameraInitialized = true
//        return cameraPreview
//    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        readyCamera()
        Thread {
            mjpegServer.startServer()
        }.start()
        return START_STICKY
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun readyCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        preview.holder.apply {
            setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
            addCallback(this@LegacyCameraService)
        }
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            0,
            PixelFormat.UNKNOWN
        )

        windowManager.addView(preview, params)

//
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
//        cameraProviderFuture.addListener({
//            cameraProcessProvider = cameraProviderFuture.get()
//
//            val turnFlashOn = cameraSettings.flashWhenObserved
//
//            imageAnalysis.setAnalyzer(callbackExecutorPool) { image ->
//                synchronized(listenerCount) {
//                    if (listenerCount > 0 || latestFrame.isEmpty()) {
//                        val buffer = image.YUV420toNV21().NV21toJPEG(image.width, image.height, 100) ?: ByteArray(0)
//                        if (buffer.isNotEmpty()) {
//                            synchronized(latestFrame) {
//                                latestFrame = buffer
//                            }
//                        }
//                    }
//                }
//                image.close()
//            }
//
//            cameraProcessProvider?.unbindAll()
//            val camera = cameraProcessProvider?.bindToLifecycle(
//                this,
//                cameraSelector,
//                imageCapture,
//                imageAnalysis
//            )
//
//            if (turnFlashOn) {
//                camera?.cameraControl?.enableTorch(true)
//            }
//            cameraInitialized = true
//        }, ContextCompat.getMainExecutor(applicationContext))
//        octoprintHandler.isCameraServerRunning = true
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        try {
            camera.setPreviewDisplay(holder)
            camera.parameters.pictureFormat = ImageFormat.NV21
            val rect = Rect(0, 0, camera.parameters.previewSize.width, camera.parameters.previewSize.height)
            val out = ByteArrayOutputStream()
            camera.setPreviewCallback { bytes, cam ->
                YuvImage(
                    bytes,
                    ImageFormat.NV21,
                    cam.parameters.previewSize.width,
                    cam.parameters.previewSize.height, null
                ).compressToJpeg(rect, 100, out)

                synchronized(latestFrame) {
                    latestFrame = out.toByteArray()
                }
                out.reset()
            }
            camera.startPreview()
        } catch (e: Throwable) {
        }
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
        camera.setPreviewDisplay(p0)
        camera.startPreview()
    }

    override fun surfaceDestroyed(p0: SurfaceHolder?) {
    }
}