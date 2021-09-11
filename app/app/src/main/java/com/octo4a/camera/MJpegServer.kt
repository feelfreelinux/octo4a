package com.octo4a.camera

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
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
    override fun serve(session: IHTTPSession?): Response {
        when (session?.uri) {
            "/snapshot" -> {
                    var res: Response? = null
                    kotlin.runCatching {
                        runBlocking {
                            val data = frameProvider.takeSnapshot()
                            val inputStream = ByteArrayInputStream(data)

                            res = newFixedLengthResponse(
                                Response.Status.OK,
                                "image/jpeg",
                                inputStream,
                                data.size.toLong()
                            )
                        }
                    }.onFailure {
                    }

                return res ?: newFixedLengthResponse(
                    "<h1>Failed to fetch image</h1>"
                )
            }
            "/mjpeg" -> {
                return MjpegResponse(frameProvider)
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