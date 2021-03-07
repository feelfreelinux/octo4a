package com.octo4a.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

data class CameraDescription(val id: String, val megapixels: Int, val lensFacing: Int)

fun CameraManager.enumerateDevices(): List<CameraDescription> {
    return cameraIdList.map {
        val characteristics = getCameraCharacteristics(it)
        val sensorInfoPixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val megapixels = (sensorInfoPixelArraySize.width * sensorInfoPixelArraySize.height) / 1024000
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        CameraDescription(it, megapixels, facing)
    }
}