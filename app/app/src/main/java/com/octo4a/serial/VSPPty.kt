package com.octo4a.serial

class VSPPty {
    init {
        System.loadLibrary("vsp-pty")
    }

    external fun setVSPListener(listener: VSPListener)
    external fun writeData(data: ByteArray)
    external fun getBaudrate(data: Int): Int
    external fun runPtyThread()

}