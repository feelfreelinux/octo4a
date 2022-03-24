package com.octo4a.camera

import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException
import kotlin.experimental.inv

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
        val nv21 = ByteArray((image.width * image.height * 1.5f).toInt())
        return if (!yuv420toNv21(
                image.width,
                image.height,
                image.planes[0].buffer,  // Y buffer
                image.planes[1].buffer,  // U buffer
                image.planes[2].buffer,  // V buffer
                image.planes[0].pixelStride,  // Y pixel stride
                image.planes[1].pixelStride,  // U/V pixel stride
                image.planes[0].rowStride,  // Y row stride
                image.planes[1].rowStride,  // U/V row stride
                nv21
            )
        ) {
            null
        } else nv21
    }


    fun yuvToNv21Slow(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V
        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)
        var pos = 0
        if (rowStride == width) { // likely
            yBuffer[nv21, 0, ySize]
            pos += ySize
        } else {
            var yBufferPos = -rowStride.toLong() // not an actual position
            while (pos < ySize) {
                yBufferPos += rowStride.toLong()
                yBuffer.position(yBufferPos.toInt())
                yBuffer[nv21, pos, width]
                pos += width
            }
        }
        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        assert(rowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)
        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            val savePixel = vBuffer[1]
            try {
                vBuffer.put(1, savePixel.inv() as Byte)
                if (uBuffer[0] == savePixel.inv() as Byte) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer[nv21, ySize, 1]
                    uBuffer[nv21, ySize + 1, uBuffer.remaining()]
                    return nv21 // shortcut
                }
            } catch (ex: ReadOnlyBufferException) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel)
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer[vuPos]
                nv21[pos++] = uBuffer[vuPos]
            }
        }
        return nv21
    }


}


