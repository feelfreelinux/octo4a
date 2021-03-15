package com.octo4a.serial

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.nio.charset.Charset
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.lang.Exception
import java.util.concurrent.Executor
import java.util.concurrent.Executors


class VirtualSerialDriver(private val usbManager: UsbManager): VSPListener, SerialInputOutputManager.Listener {
    val pty by lazy { VSPPty() }

    var selectedDevice: UsbSerialDriver? = null
    var port: UsbSerialPort? = null
    var connection: UsbDeviceConnection? = null
    var serialInputManager: SerialInputOutputManager? = null
    var currentBaudrate = -1
    var ptyThread: Thread? = null


    fun initializeVSP() {
        pty.setVSPListener(this)
    }

    fun handlePtyThread() {
        ptyThread = Thread {
            pty.runPtyThread()
        }
        ptyThread?.start()
    }

    fun stopPtyThread() {
        ptyThread?.interrupt()
    }

    fun updateDevicesList(context: Context, intent: String) {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            return
        }

        val device = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).first()
        if (!usbManager.hasPermission(device!!.device)) {
            val mPendingIntent =
                PendingIntent.getBroadcast(context, 0, Intent(intent), 0)
            usbManager.requestPermission(device.device, mPendingIntent)
        } else {
            selectedDevice = device
        }
    }

    override fun onDataReceived(data: SerialData?) {
        data?.apply {
            if (isStartPacket || currentBaudrate != baudrate) {
                if (port?.isOpen == true) {
                    port?.close()
                }
                connection = usbManager.openDevice(selectedDevice?.device)
                port = selectedDevice!!.ports.first()

                port?.open(connection)

                Log.v("COCKRN", (data.c_cflag and 768).toString())

                // @TODO get it from flags
                port?.setParameters(pty.getBaudrate(baudrate), 8, UsbSerialPort.STOPBITS_2, UsbSerialPort.PARITY_NONE)
                currentBaudrate = baudrate

                serialInputManager = SerialInputOutputManager(port, this@VirtualSerialDriver)
                Executors.newSingleThreadExecutor().submit(serialInputManager)
            }

            if (data.data.size > 1) {
                port?.write(data.serialData, 0)
            }
        }
    }

    override fun onNewData(data: ByteArray) {
        pty.writeData(data)
    }

    override fun onRunError(e: Exception?) {
    }
}