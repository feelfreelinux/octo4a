package com.octo4a.camera


class RotateUtils {
    companion object {
        fun rotate270(nv21_data: ByteArray, width: Int, height: Int): ByteArray? {
            try {
                val y_size = width * height
                val buffser_size = y_size * 3 / 2
                val nv21_rotated = ByteArray(buffser_size)
                var i = 0

                // Rotate the Y luma
                for (x in width - 1 downTo 0) {
                    var offset = 0
                    for (y in 0 until height) {
                        nv21_rotated[i] = nv21_data[offset + x]
                        i++
                        offset += width
                    }
                }

                // Rotate the U and V color components
                i = y_size
                var x = width - 1
                while (x > 0) {
                    var offset = y_size
                    for (y in 0 until height / 2) {
                        nv21_rotated[i] = nv21_data[offset + (x - 1)]
                        i++
                        nv21_rotated[i] = nv21_data[offset + x]
                        i++
                        offset += width
                    }
                    x = x - 2
                }
                return nv21_rotated
            } catch (e: Exception) {
            }
            return null
        }

        /**
         * 旋转180度
         */
        fun rotate180(nv21_data: ByteArray, width: Int, height: Int): ByteArray? {
            try {
                val y_size = width * height
                val buffser_size = y_size * 3 / 2
                val nv21_rotated = ByteArray(buffser_size)
                var i = 0
                var count = 0
                i = y_size - 1
                while (i >= 0) {
                    nv21_rotated[count] = nv21_data[i]
                    count++
                    i--
                }
                i = buffser_size - 1
                while (i >= y_size) {
                    nv21_rotated[count++] = nv21_data[i - 1]
                    nv21_rotated[count++] = nv21_data[i]
                    i -= 2
                }
                return nv21_rotated
            } catch (e: Exception) {
            }
            return null
        }

        /**
         * 旋转90度
         */
        fun rotate90(nv21_data: ByteArray, width: Int, height: Int): ByteArray? {
            try {
                val y_size = width * height
                val buffser_size = y_size * 3 / 2
                val nv21_rotated = ByteArray(buffser_size)

                // Rotate the Y luma
                var i = 0
                val startPos = (height - 1) * width
                for (x in 0 until width) {
                    var offset = startPos
                    for (y in height - 1 downTo 0) {
                        nv21_rotated[i] = nv21_data[offset + x]
                        i++
                        offset -= width
                    }
                }
                // Rotate the U and V color components
                i = buffser_size - 1
                var x = width - 1
                while (x > 0) {
                    var offset = y_size
                    for (y in 0 until height / 2) {
                        nv21_rotated[i] = nv21_data[offset + x]
                        i--
                        nv21_rotated[i] = nv21_data[offset + (x - 1)]
                        i--
                        offset += width
                    }
                    x = x - 2
                }
                return nv21_rotated
            } catch (e: Exception) {
            }
            return null
        }


        fun rotate(bytes: ByteArray, width: Int, height: Int, rotateDegree: Int): ByteArray? {
            return if (rotateDegree == 270) {
                rotate270(bytes, width, height)
            } else if (rotateDegree == 180) {
                rotate180(bytes, width, height)

            } else {
                rotate90(bytes, width, height)
            }
        }
    }
}