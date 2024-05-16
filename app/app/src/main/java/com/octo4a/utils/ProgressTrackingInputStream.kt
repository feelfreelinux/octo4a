package com.octo4a.utils

import java.io.FilterInputStream
import java.io.InputStream
class ProgressTrackingInputStream(
    inputStream: InputStream,
    private val progressListener: (Long) -> Unit
) : FilterInputStream(inputStream) {

    private var totalBytesRead: Long = 0

    override fun read(): Int {
        val byte = super.read()
        if (byte >= 0) {
            totalBytesRead++
            progressListener(totalBytesRead)
        }
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val bytesRead = super.read(b, off, len)
        if (bytesRead > 0) {
            totalBytesRead += bytesRead
            progressListener(totalBytesRead)
        }
        return bytesRead
    }
}