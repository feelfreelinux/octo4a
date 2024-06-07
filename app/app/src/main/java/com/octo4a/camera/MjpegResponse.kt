package com.octo4a.camera

import fi.iki.elonen.NanoHTTPD
import java.io.OutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class MjpegResponse(private val frameProvider: MJpegFrameProvider) :
    NanoHTTPD.Response(Status.OK, "multipart/x-mixed-replace; boundary=--octo4a", null, 0) {
  override fun send(outputStream: OutputStream) {
    val registered = frameProvider.registerListener()
    kotlin
        .runCatching {
          if (registered) {
            sendStream(outputStream)
          } else {
            sendError(outputStream, 500, "Internal Server Error - Camera Not Initialized")
          }
        }
        .onFailure { outputStream.close() }
        .onSuccess { outputStream.close() }

    if (registered) {
      frameProvider.unregisterListener()
    }
  }

  private fun sendStream(outputStream: OutputStream) {
    val gmtFrmt = SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US)
    gmtFrmt.timeZone = TimeZone.getTimeZone("GMT")

    val pw = PrintWriter(outputStream)
    pw.apply {
      print("HTTP/1.1 ${status.description} \r\n")
      print("Date: ${gmtFrmt.format(Date())}\r\n")
      print("Connection: close\r\n")
      print(
          "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n")
      print("Expires: 0\r\n")
      print("Max-Age: 0\r\n")
      print("Pragma: no-cache\r\n")
      print("Content-Type: ${mimeType}\r\n")
      print("\r\n--octo4a\r\n")
      flush()
      var frameInfo: MJpegFrameProvider.FrameInfo? = null
      while (true) {
        frameInfo = frameProvider.getNewFrame(frameInfo)
        val image = frameInfo.image ?: ByteArray(0)
        if (image.isNotEmpty()) {
          write("Content-type: image/jpeg\r\n")
          write("Content-Length: ${image?.size}\r\n")
          write("\r\n")
          flush()
          outputStream.write(image, 0, image.size)
          outputStream.flush()
          print("\r\n--octo4a\r\n")
          flush()
        }
      }
    }
  }

  private fun sendError(outputStream: OutputStream, statusCode: Int, message: String) {
    val pw = PrintWriter(outputStream)
    pw.apply {
      print("HTTP/1.1 $statusCode $message\r\n")
      print("Content-Length: ${message.length}\r\n")
      print("\r\n$message")
      flush()
    }
  }
}
