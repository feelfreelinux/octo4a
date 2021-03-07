package com.octo4a.camera

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*

interface MJpegFrameProvider {
    val newestFrame: ByteArray
}

// Simple http server hosting mjpeg stream along with
class MJpegServer(port: Int, private val frameProvider: MJpegFrameProvider) {
    companion object {
        private val CRLF = byteArrayOf(0x0d, 0x0a)
        private const val MJPEG_BOUNDARY = "--frame"
        private const val CONTENT_TYPE = "multipart/x-mixed-replace; boundary=frame"
    }

    private val server by lazy {
        embeddedServer(Netty, port = port) {
            routing {
                get("/snapshot") {
                    call.respondBytes(frameProvider.newestFrame, ContentType.Image.JPEG, HttpStatusCode.OK)
                }

                get("/mjpeg") {
                    call.respondBytesWriter(ContentType.parse(CONTENT_TYPE), status = HttpStatusCode.OK) {
                        while (!isClosedForWrite) {
                            writeFully("--".toByteArray() + MJPEG_BOUNDARY.toByteArray() + CRLF)
                            writeFully("Content-Type: image/jpeg".toByteArray() + CRLF)
                            writeFully("Content-Length: ${frameProvider.newestFrame.size}".toByteArray() + CRLF + CRLF)
                            writeFully(frameProvider.newestFrame + CRLF)
                            flush()
                        }
                    }
                }
            }
        }
    }

    fun startServer() {
        server.start(true)
    }
}