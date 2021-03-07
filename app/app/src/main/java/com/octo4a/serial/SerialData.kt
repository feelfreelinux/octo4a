package com.octo4a.serial

import com.octo4a.utils.isBitSet

class SerialData(val data: ByteArray, val baudrate: Int, val c_iflag: Int, val c_oflag: Int, val c_cflag: Int, val c_lflag: Int) {
    companion object {
        const val TIOCPKT_DATA = 0
    }

    val isStartPacket = data[0].toInt().isBitSet(TIOCPKT_DATA)
    val serialData: ByteArray
        get() = data.copyOfRange(1, data.size)
}