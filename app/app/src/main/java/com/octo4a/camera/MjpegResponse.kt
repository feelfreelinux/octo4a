package com.octo4a.camera

import fi.iki.elonen.NanoHTTPD
import java.io.OutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class MjpegResponse(private val frameProvider: MJpegFrameProvider): NanoHTTPD.Response(Status.OK, "multipart/x-mixed-replace; boundary=--octo4a", null, 0) {
    override fun send(outputStream: OutputStream) {
        val gmtFrmt = SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        gmtFrmt.timeZone = TimeZone.getTimeZone("GMT")

        frameProvider.registerListener()
        kotlin.runCatching {
            val pw = PrintWriter(outputStream)
            pw.apply {
                print("HTTP/1.1 ${status.description} \r\n")
                print("Date: ${gmtFrmt.format(Date())}\r\n")
                print("Connection: close\r\n");
                print("Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n");
                print("Expires: 0\r\n");
                print("Max-Age: 0\r\n");
                print("Pragma: no-cache\r\n");
                print("Content-Type: ${mimeType}\r\n")
                print("\r\n--octo4a\r\n")
                flush()

                while (true) {
                    val frame = frameProvider.newestFrame
                    if (frame.isNotEmpty()) {
                        write("Content-type: image/jpeg\r\n")
                        write("Content-Length: ${frame.size}\r\n")
                        write("\r\n")
                        flush()
                        outputStream.write(frame, 0, frame.size)
                        outputStream.flush()
                        print("\r\n--octo4a\r\n")
                        flush()
                    }

//                    Thread.sleep(10)
                }
            }
        }.onFailure {
            outputStream.close()
        }.onSuccess {
            outputStream.close()
        }

        frameProvider.unregisterListener()
    }
}