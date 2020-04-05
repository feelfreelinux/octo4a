package io.feelfreelinux.octo4a.octo4a

import android.R
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketTimeoutException

class MJpegServer(val fetchLastJpegFrame: () -> ByteArray) : Runnable {
    companion object {
        const val SERVER_PORT = 5001
    }
    var server: ServerSocket? = null

    var running = true

    fun stopServer() {
        running = false
        server?.close()
    }

    override fun run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)

        server = ServerSocket(SERVER_PORT)
        server!!.soTimeout = 5000

        val boundary = "OctoMjpegBoundary"

        while (running) {
            try {
                val socket = server?.accept()
                socket?.let {
                    Thread {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                        try {
                            val stream = DataOutputStream(socket.getOutputStream())

                            stream.write(("HTTP/1.0 200 OK\r\n" +
                                    "Server: OctoMjpegServer\r\n" +
                                    "Connection: close\r\n" +
                                    "Max-Age: 0\r\n" +
                                    "Expires: 0\r\n" +
                                    "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                                    "Pragma: no-cache\r\n" +
                                    "Content-Type: multipart/x-mixed-replace; boundary=" + boundary + "\r\n" +
                                    "\r\n" +
                                    "--" + boundary + "\r\n").toByteArray())
                            stream.flush()

                            while (running) {
                                val frame = fetchLastJpegFrame()

                                stream.write(("Content-type: image/jpeg\r\n" +
                                        "Content-Length: " + frame.size + "\r\n" +
                                        "\r\n").toByteArray())
                                stream.write(frame)
                                stream.write("\r\n--$boundary\r\n".toByteArray())
                            }
                            stream.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            } catch (ste: SocketTimeoutException) {
                // continue silently
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }

            if (SERVER_PORT!= server?.localPort) {
                try {
                    server!!.close()
                    server = ServerSocket(SERVER_PORT)
                    server!!.soTimeout = 5000
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
    }
}