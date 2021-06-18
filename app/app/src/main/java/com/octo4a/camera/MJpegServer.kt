package com.octo4a.camera

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

interface MJpegFrameProvider {
    val newestFrame: ByteArray

    suspend fun takeSnapshot(): ByteArray
    fun registerListener()
    fun unregisterListener()
}

// Simple http server hosting mjpeg stream along with
class MJpegServer(port: Int, private val frameProvider: MJpegFrameProvider): NanoHTTPD(port) {
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private val CRLF = byteArrayOf(0x0d, 0x0a)
        private const val MJPEG_BOUNDARY = "frame"
        private const val OUTPUT_BUFFERED_SIZE = 5 * 1024
        private const val CONTENT_TYPE = "multipart/x-mixed-replace; boundary=--frame"
    }

    override fun serve(session: IHTTPSession?): Response {
        when (session?.uri) {
            "/snapshot" -> {
                val output = PipedOutputStream()
                val input = PipedInputStream(output)
                val bufferedOutput = BufferedOutputStream(output, OUTPUT_BUFFERED_SIZE)
                scope.launch {
                    val data = frameProvider.takeSnapshot()
                    bufferedOutput.use {
                        it.write(data)
                    }
                }

                return newChunkedResponse(Response.Status.OK, "image/jpeg", input)
            }
            "/mjpeg" -> {
                val output = PipedOutputStream()
                val input = PipedInputStream(output)
                val bufferedOutput = BufferedOutputStream(output, OUTPUT_BUFFERED_SIZE)
                scope.launch {
                    kotlin.runCatching {
                        delay(500)
                        frameProvider.registerListener()
                        while (true) {
                            val frameData = frameProvider.newestFrame
                            bufferedOutput.let {
                                it.write("--frame\r\n".toByteArray())
                                it.write("Content-type: image/jpeg\r\n".toByteArray())
                                it.write("Content-Length: ${frameData.size}".toByteArray() + CRLF + CRLF)
                                it.write(frameData + CRLF)
                                it.flush()
                            }
                        }
                    }.onFailure {
                        frameProvider.unregisterListener()
                        kotlin.runCatching {
                            bufferedOutput.close()
                        }
                    }
                }

                return newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=frame", input)
            }
            else -> return newFixedLengthResponse(
                "<html><body>"
                        + "<h1>GET /snapshot</h1><p>GET a current JPEG image.</p>"
                        + "<h1>GET /mjpeg</h1><p>GET MJPEG frames.</p>"
                        + "</body></html>"
            )
        }
    }

    fun startServer() {
        start()
    }

    fun stopServer() {
        stop()
    }
}