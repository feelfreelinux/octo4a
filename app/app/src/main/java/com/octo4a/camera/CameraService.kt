package com.octo4a.camera

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.hardware.camera2.CameraManager
import android.os.IBinder
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.octo4a.R
import com.octo4a.octoprint.OctoPrintService
import com.octo4a.ui.MainActivity
import com.octo4a.utils.NV21toJPEG
import com.octo4a.utils.log
import com.octo4a.utils.preferences.MainPreferences
import org.koin.android.ext.android.inject
import java.util.concurrent.Executors

class CameraService : LifecycleService(), MJpegFrameProvider {
    private var latestFrame: ByteArray = ByteArray(0)
    private var listenerCount = 0

    private val cameraSettings: MainPreferences by inject()
    private val manager: CameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    override val newestFrame: ByteArray
        get() = synchronized(latestFrame) { return latestFrame }

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
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size.parseSize(cameraSettings.selectedResolution))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(callbackExecutorPool) { image ->
                synchronized(listenerCount) {
                    if (listenerCount > 0) {
                        val buffer = imageToByteArray(image)?.NV21toJPEG(image.width, image.height, 100) ?: ByteArray(0)
                        synchronized(latestFrame) {
                            latestFrame = buffer
                        }
                    }
                }
                image.close()
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(applicationContext))
    }
    private fun imageToByteArray(image: ImageProxy): ByteArray? {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        //U and V are swapped
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        return yuvImage.yuvData
    }
    private val notificationBuilder by lazy {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        NotificationCompat.Builder(this, OctoPrintService.CHANNEL_ID)
            .setContentTitle("OctoPrint")
            .setContentText("Octoprint something something")
            .setVibrate(null)
            .setSmallIcon(R.drawable.ic_print_24px)
            .setContentIntent(pendingIntent)
    }
}