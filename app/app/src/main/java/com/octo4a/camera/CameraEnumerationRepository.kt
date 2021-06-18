package com.octo4a.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.octo4a.utils.isServiceRunning

data class CameraSize(val width: Int, val height: Int)

data class CameraDescription(val id: String, val megapixels: Int, val lensFacing: Int, val sizes: List<CameraSize>)

fun CameraSize.readableString(): String {
    return "${width}x${height}"
}

fun CameraDescription.getRecommendedSize(): CameraSize? {
    return sizes.sortedBy { it.width }.firstOrNull { it.width >= 1000 }
}

val CameraDescription.isBackFacing: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            lensFacing == CameraSelector.LENS_FACING_BACK
        } else {
            lensFacing == Camera.CameraInfo.CAMERA_FACING_BACK
        }
}

fun CameraDescription.describeString(): String{
    return (if (isBackFacing) "Back" else "Front") + " camera, $megapixels MP"
}


class CameraEnumerationRepository(val context: Context) {
    val enumeratedCameras = MutableLiveData<List<CameraDescription>>()
    var camerasEnumerated = false

    fun cameraWithId(id: String): CameraDescription? {
        return enumeratedCameras.value?.firstOrNull { it.id == id }
    }

    fun enumerateCameras() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (!camerasEnumerated) {
            var cams = mutableListOf<CameraDescription>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cams = manager.cameraIdList.map {
                    val characteristics = manager.getCameraCharacteristics(it)
                    val sensorInfoPixelArraySize =
                        characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    val megapixels =
                        (sensorInfoPixelArraySize.width * sensorInfoPixelArraySize.height) / 1024000
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    val configs = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                    )
                    val sizes = configs?.getOutputSizes(ImageFormat.YUV_420_888)
                        ?.map { size -> CameraSize(size.width, size.height) }
                    CameraDescription(it, megapixels, facing, sizes ?: emptyList())
                }.toMutableList()
            } else {
                val needToRestartService = context.isServiceRunning(LegacyCameraService::class.java)

                if (needToRestartService) {
                    context.stopService(Intent(context, LegacyCameraService::class.java))
                }
                // Utilize the old Camera1 api
                val cameras = Camera.getNumberOfCameras()
                for (i in 0 until cameras) {
                    val cameraInfo = Camera.CameraInfo()
                    Camera.getCameraInfo(i, cameraInfo)

                    val cam = Camera.open(i)

                    val pictureSize = cam.parameters.pictureSize
                    val megaPixels = (pictureSize.width * pictureSize.height) / 1024000
                    cams.add(CameraDescription(
                        i.toString(),
                        megaPixels,
                        cameraInfo.facing,
                        cam.parameters.supportedPreviewSizes.map {
                            CameraSize(it.width, it.height)
                        }
                    ))

                    cam.release()
                }

                if (needToRestartService) {
                    // Restart camera service
                    context.startService(Intent(context, LegacyCameraService::class.java))
                }
            }

            enumeratedCameras.postValue(cams)
            camerasEnumerated = true
        }
    }
}