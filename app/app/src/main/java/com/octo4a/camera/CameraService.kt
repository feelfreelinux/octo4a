package com.octo4a.camera

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.octo4a.ui.MainActivity
import com.octo4a.R
import com.octo4a.octoprint.OctoPrintService
import com.octo4a.utils.NV21toJPEG
import com.octo4a.utils.YUV420toNV21

class CameraService : Service(), MJpegFrameProvider {
    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val imageReader by lazy { ImageReader.newInstance(500, 800, ImageFormat.YUV_420_888, 2) }
    var cameraDevice: CameraDevice? = null
    var cameraCaptureSession: CameraCaptureSession? = null
    private val backgroundProcessingHandler by lazy { Handler() }
    private var latestFrame: ByteArray = ByteArray(0)

    override val newestFrame: ByteArray
        get() = synchronized(latestFrame) { return latestFrame }

    private val mjpegServer by lazy { MJpegServer(5001, this) }

    private val cameraCaptureRequest by lazy {
        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder?.addTarget(imageReader!!.surface)

        // Setup image listener on separate handler
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                image.YUV420toNV21().NV21toJPEG(image.width, image.height, 100)?.let {
                    synchronized(latestFrame) {
                        latestFrame = it
                    }
                }
            }

            image?.close()
        }, backgroundProcessingHandler)
        builder?.build()
    }

    // Selects first available back camera
    private val selectedCamera by lazy {
        cameraManager.cameraIdList.firstOrNull {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    // Starts capturing session when camera ready
    private val cameraStateCallback: CameraCaptureSession.StateCallback  = object : CameraCaptureSession.StateCallback() {
        override fun onReady(session: CameraCaptureSession) {
            super.onReady(session)
            cameraCaptureSession = session

            cameraCaptureRequest?.let {
                session.setRepeatingRequest(cameraCaptureRequest!!, null, null)
            }
        }

        override fun onConfigured(p0: CameraCaptureSession) {}
        override fun onConfigureFailed(p0: CameraCaptureSession) {}
    }

    // Waits for camera to be open
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(currentCameraDevice: CameraDevice) {
            cameraDevice = currentCameraDevice
            cameraDevice!!.createCaptureSession(listOf(imageReader.surface), cameraStateCallback, null)
        }

        override fun onDisconnected(currentCameraDevice: CameraDevice) {
            currentCameraDevice.close()
            cameraDevice = null
        }

        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
            currentCameraDevice.close()
            cameraDevice = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        readyCamera()
        Thread {
            mjpegServer.startServer()
        }.start()

        startForeground(OctoPrintService.NOTIFICATION_ID, notificationBuilder.build())
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraCaptureSession?.abortCaptures()
    }

    private fun readyCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        cameraManager.openCamera(selectedCamera!!, stateCallback, null)
    }

    private val notificationBuilder by lazy {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        NotificationCompat.Builder(this, OctoPrintService.CHANNEL_ID)
            .setContentTitle("OctoPrint")
            .setContentText("Octoprint something something")
            .setVibrate(null)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
    }
}