package com.octo4a.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import androidx.camera.core.CameraSelector


data class CameraDescription(val id: String, val megapixels: Int, val lensFacing: Int, val sizes: List<Size>)

fun CameraDescription.describeString(): String{
    val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
    return (if (isFront)  "Front" else "Back") + " camera, $megapixels MP"
}

fun CameraDescription.getRecommendedSize(): Size? {
    return sizes.sortedBy { it.width }.firstOrNull { it.width >= 1000 }
}

fun CameraManager.enumerateDevices(): List<CameraDescription> {
    return cameraIdList.map {
        val characteristics = getCameraCharacteristics(it)
        val sensorInfoPixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val megapixels = (sensorInfoPixelArraySize.width * sensorInfoPixelArraySize.height) / 1024000
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val configs = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        val sizes = configs?.getOutputSizes(ImageFormat.YUV_420_888)?.toList()
        CameraDescription(it, megapixels, facing, sizes ?: emptyList())
    }
}

fun CameraManager.cameraWithId(id: String): CameraDescription? {
    return enumerateDevices().firstOrNull { it.id == id }
}

fun Size.readableString(): String {
    return "${width}x${height}"
}