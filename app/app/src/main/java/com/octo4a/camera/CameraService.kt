package com.octo4a.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.IBinder
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.octo4a.utils.NV21toJPEG
import com.octo4a.utils.YUV420toNV21
import com.octo4a.utils.log
import com.octo4a.utils.preferences.MainPreferences
import org.koin.android.ext.android.inject
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class CameraService : LifecycleService(), MJpegFrameProvider {
    private var latestFrame: ByteArray = ByteArray(0)
    private var listenerCount = 0
    private var imageCapture: ImageCapture? = null

    private val cameraSettings: MainPreferences by inject()
    private val manager: CameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }
    private val captureExecutor by lazy { Executors.newCachedThreadPool() }

    override val newestFrame: ByteArray
        get() = synchronized(latestFrame) { return latestFrame }

    override suspend fun takeSnapshot(): ByteArray = suspendCoroutine<ByteArray> {
        if (imageCapture == null) {
            it.resume(ByteArray(0))
        } else {
            imageCapture?.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity()).also { array -> buffer.get(array) }
                    super.onCaptureSuccess(image) //Closes the image
                    image.close()
                    it.resume(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    log { "Single capture error: $exception" }
                    it.resume(ByteArray(0))
                }
            })
        }
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

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Thread {
            mjpegServer.stopServer()
        }.start()
    }

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

        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.Builder().apply {
                requireLensFacing(manager.cameraWithId(cameraSettings.selectedCamera!!)?.lensFacing ?: CameraSelector.LENS_FACING_FRONT)
            }.build()
            manager.cameraWithId(cameraSettings.selectedCamera!!)?.sizes?.forEach {
                log { it.readableString() }
            }

            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size.parseSize(cameraSettings.selectedResolution ?: "1280x720"))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size.parseSize(cameraSettings.selectedResolution ?: "1280x720"))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val turnFlashOn = cameraSettings.flashWhenObserved

            imageAnalysis.setAnalyzer(callbackExecutorPool) { image ->
                synchronized(listenerCount) {
                    if (listenerCount > 0 || latestFrame.isEmpty()) {
                        val buffer = image.YUV420toNV21().NV21toJPEG(image.width, image.height, 100) ?: ByteArray(0)
                        if (buffer.isNotEmpty()) {
                            synchronized(latestFrame) {
                                latestFrame = buffer
                            }
                        }
                    }
                }
                image.close()
            }

            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                imageCapture,
                imageAnalysis
            )

            if (turnFlashOn) {
                camera.cameraControl.enableTorch(true)
            }
        }, ContextCompat.getMainExecutor(applicationContext))
    }
}