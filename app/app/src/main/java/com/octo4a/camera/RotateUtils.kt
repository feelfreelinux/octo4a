package com.octo4a.camera

class RotateUtils {
    companion object {
        fun rotateNV21(
            yuv: ByteArray,
            width: Int,
            height: Int,
            rotation: Int
        ): ByteArray? {
            if (rotation == 0) return yuv
            require(!(rotation % 90 != 0 || rotation < 0 || rotation > 270)) { "0 <= rotation < 360, rotation % 90 == 0" }
            val output = ByteArray(yuv.size)
            val frameSize = width * height
            val swap = rotation % 180 != 0
            val xflip = rotation % 270 != 0
            val yflip = rotation >= 180
            for (j in 0 until height) {
                for (i in 0 until width) {
                    val yIn = j * width + i
                    val uIn = frameSize + (j shr 1) * width + (i and 1.inv())
                    val vIn = uIn + 1
                    val wOut = if (swap) height else width
                    val hOut = if (swap) width else height
                    val iSwapped = if (swap) j else i
                    val jSwapped = if (swap) i else j
                    val iOut = if (xflip) wOut - iSwapped - 1 else iSwapped
                    val jOut = if (yflip) hOut - jSwapped - 1 else jSwapped
                    val yOut = jOut * wOut + iOut
                    val uOut = frameSize + (jOut shr 1) * wOut + (iOut and 1.inv())
                    val vOut = uOut + 1
                    output[yOut] = (0xff and yuv[yIn].toInt()).toByte()
                    output[uOut] = (0xff and yuv[uIn].toInt()).toByte()
                    output[vOut] = (0xff and yuv[vIn].toInt()).toByte()
                }
            }
            return output
        }
    }
}