package com.octo4a.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.os.Binder
import android.os.IBinder
import android.renderscript.*
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleService
import com.octo4a.repository.LoggerRepository
import com.octo4a.repository.OctoPrintHandlerRepository
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
    private val logger: LoggerRepository by inject()

    override fun getNewFrame(prevFrame: MJpegFrameProvider.FrameInfo?): MJpegFrameProvider.FrameInfo {
      return MJpegFrameProvider.FrameInfo(latestFrame, id=-1)
  }

    inner class LocalBinder : Binder() {
        fun getService(): LegacyCameraService = this@LegacyCameraService
    }

    private val windowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private val binder = LocalBinder()

    override suspend fun takeSnapshot(): ByteArray = suspendCoroutine {
    }

    override fun registerListener(): Boolean {
        synchronized(listenerCount) {
            listenerCount++
        }
        logger.log(this) { "Legacy camera register listener" }
        return true
    }

    override fun unregisterListener() {
        synchronized(listenerCount) {
            listenerCount--
        }
        logger.log(this) { "Legacy camera unregister listener" }
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
        camera.release()
        octoprintHandler.isCameraServerRunning = false
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

        preview.holder.apply {
            setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
            addCallback(this@LegacyCameraService)
        }
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )

        windowManager.addView(preview, params)
    }
    val renderScript by lazy { RenderScript.create(applicationContext) }

    private fun nv21ToBitmap(yuvByteArray: ByteArray, width: Int, height: Int): Bitmap {

        val yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript))

        val yuvType = Type.Builder(renderScript, Element.U8(renderScript)).setX(yuvByteArray.size)
        val allocationIn = Allocation.createTyped(renderScript, yuvType.create(), Allocation.USAGE_SCRIPT)

        val rgbaType = Type.Builder(renderScript, Element.RGBA_8888(renderScript)).setX(width).setY(height)
        val allocationOut = Allocation.createTyped(renderScript, rgbaType.create(), Allocation.USAGE_SCRIPT)

        allocationIn.copyFrom(yuvByteArray)

        yuvToRgbIntrinsic.setInput(allocationIn)
        yuvToRgbIntrinsic.forEach(allocationOut)

        val bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        allocationOut.copyTo(bmpout)

        return bmpout
    }
    override fun surfaceCreated(holder: SurfaceHolder?) {
        try {
            camera.setPreviewDisplay(holder)
            camera.parameters.pictureFormat = ImageFormat.NV21
            val rect = Rect(0, 0, camera.parameters.previewSize.width, camera.parameters.previewSize.height)
            val out = ByteArrayOutputStream()
            camera.setPreviewCallback { bytes, cam ->

                val bitmap = nv21ToBitmap(bytes, cam.parameters.previewSize.width, cam.parameters.previewSize.height)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                synchronized(latestFrame) {
                    latestFrame = out.toByteArray()
                }
                bitmap.recycle()
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