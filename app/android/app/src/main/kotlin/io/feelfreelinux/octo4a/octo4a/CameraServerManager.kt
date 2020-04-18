package io.feelfreelinux.octo4a.octo4a

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import java.util.concurrent.locks.ReentrantReadWriteLock

class CameraServerManager(val cameraManager: CameraManager) {
    companion object {
        const val TAG = "OCTO4A-CAM"
    }
    // Camera2-related stuff
    private var previewSize: Size? = null
    private var cameraDevice: CameraDevice? = null
    private var captureRequest: CaptureRequest? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private val frameLock = ReentrantReadWriteLock()

    // You can start service in 2 modes - 1.) with preview 2.) without preview (only bg processing)
    private var shouldShowPreview = true

    fun getJpegFrame(): ByteArray? {
        try {
            frameLock.readLock().lock()
            return lastFrameInJpeg
        } finally {
            frameLock.readLock().unlock()
        }
    }

    private fun setJpegFrame(stream: ByteArray) {
        try {
            frameLock.writeLock().lock()
            lastFrameInJpeg = stream
        } finally {
            frameLock.writeLock().unlock()
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
        ) {}

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
        ) {}
    }

    var lastFrameInJpeg: ByteArray? = null

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader?.acquireLatestImage()



        image?.let {
            // Log.v(TAG, image!!.format.toString())

            setJpegFrame(ImageUtils.NV21toJPEG(
                    ImageUtils.YUV420toNV21(image),
                    image.width, image.height, 100))
        }


        // Process image here..ideally async so that you don't block the callback
        // ..

        image?.close()
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(currentCameraDevice: CameraDevice) {
            cameraDevice = currentCameraDevice
            createCaptureSession()
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


    var serverThread: Thread? = null
    var serverRunner: MJpegServer? = null

    fun start(width: Int, height: Int) {
        initCam(width, height)

        serverRunner = MJpegServer {
            getJpegFrame() ?: ByteArray(0)
        }

        serverThread = Thread(serverRunner)

        serverThread!!.start()
    }


    @SuppressLint("MissingPermission")
    private fun initCam(width: Int, height: Int) {

        // cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        var camId: String? = null

        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                camId = id
                break
            }
        }

        previewSize = Size(width, height)

        cameraManager.openCamera(camId, stateCallback, null)
    }

    fun getAllSupportedSizes(): List<Size> {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                return map?.getOutputSizes(SurfaceTexture::class.java)?.toList() ?: emptyList()
            }
        }

        return listOf()
    }

    private fun chooseSupportedSize(camId: String, textureViewWidth: Int, textureViewHeight: Int): Size {
        return Size(720, 1280)

//        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
//
//        // Get all supported sizes for TextureView
//        val characteristics = manager.getCameraCharacteristics(camId)
//        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//        val supportedSizes = map.getOutputSizes(SurfaceTexture::class.java)
//
//        supportedSizes.forEach {
//            Log.v("DUUPA", it.height.toString() + " " + it.width)
//        }
//
//        // We want to find something near the size of our TextureView
//        val texViewArea = textureViewWidth * textureViewHeight
//        val texViewAspect = textureViewWidth.toFloat()/textureViewHeight.toFloat()
//
//        val nearestToFurthestSz = supportedSizes.sortedWith(compareBy(
//                // First find something with similar aspect
//                {
//                    val aspect = if (it.width < it.height) it.width.toFloat() / it.height.toFloat()
//                    else it.height.toFloat()/it.width.toFloat()
//                    (aspect - texViewAspect).absoluteValue
//                },
//                // Also try to get similar resolution
//                {
//                    (texViewArea - it.width * it.height).absoluteValue
//                }
//        ))
//
//
//        if (nearestToFurthestSz.isNotEmpty())
//            return nearestToFurthestSz[0]
//
//        return Size(320, 200)
    }

    private fun createCaptureSession() {
        try {
            // Prepare surfaces we want to use in capture session
            val targetSurfaces = ArrayList<Surface>()

            // Prepare CaptureRequest that can be used with CameraCaptureSession
            val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                // Configure target surface for background processing (ImageReader)
                imageReader = ImageReader.newInstance(
                        previewSize!!.width, previewSize!!.height,
                        ImageFormat.YUV_420_888, 2
                )
                imageReader!!.setOnImageAvailableListener(imageListener, null)

                targetSurfaces.add(imageReader!!.surface)
                addTarget(imageReader!!.surface)

                // Set some additional parameters for the request
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }

            // Prepare CameraCaptureSession
            cameraDevice!!.createCaptureSession(targetSurfaces,
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return
                            }

                            captureSession = cameraCaptureSession
                            try {
                                // Now we can start capturing
                                captureRequest = requestBuilder.build()
                                captureSession!!.setRepeatingRequest(captureRequest!!, captureCallback, null)

                            } catch (e: CameraAccessException) {
                                Log.e(TAG, "createCaptureSession", e)
                            }

                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            Log.e(TAG, "createCaptureSession()")
                        }
                    }, null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createCaptureSession", e)
        }
    }

    fun stopCamera() {
        serverRunner?.stopServer()
        try {
            serverThread?.interrupt()
            serverThread = null
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

        } catch (e: Exception) {
            e.printStackTrace()

            throw(e)
        }
    }
}