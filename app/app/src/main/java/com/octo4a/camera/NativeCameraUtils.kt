package com.octo4a.camera

import android.media.Image
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class NativeCameraUtils {
    init {
        System.loadLibrary("yuv2rgb")
    }

    external fun yuv420toNv21(
        imageWidth: Int,
        imageHeight: Int,
        yByteBuffer: ByteBuffer?,
        uByteBuffer: ByteBuffer?,
        vByteBuffer: ByteBuffer?,
        yPixelStride: Int,
        uvPixelStride: Int,
        yRowStride: Int,
        uvRowStride: Int,
        nv21Output: ByteArray?
    ): Boolean

    fun toNv21(image: ImageProxy): ByteArray? {
        val nv21 = ByteArray((image.getWidth() * image.getHeight() * 1.5f).toInt())
        return if (!yuv420toNv21(
                image.getWidth(),
                image.getHeight(),
                image.getPlanes().get(0).getBuffer(),  // Y buffer
                image.getPlanes().get(1).getBuffer(),  // U buffer
                image.getPlanes().get(2).getBuffer(),  // V buffer
                image.getPlanes().get(0).getPixelStride(),  // Y pixel stride
                image.getPlanes().get(1).getPixelStride(),  // U/V pixel stride
                image.getPlanes().get(0).getRowStride(),  // Y row stride
                image.getPlanes().get(1).getRowStride(),  // U/V row stride
                nv21
            )
        ) {
            null
        } else nv21
    }
}


