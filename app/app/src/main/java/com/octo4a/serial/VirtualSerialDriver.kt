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
import com.octo4a.utils.log
import java.lang.Exception
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class VirtualSerialDriver(val context: Context): VSPListener, SerialInputOutputManager.Listener {
    val pty by lazy { VSPPty() }

    private val usbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    var selectedDevice: UsbSerialDriver? = null
    var port: UsbSerialPort? = null
    var connection: UsbDeviceConnection? = null
    var serialInputManager: SerialInputOutputManager? = null
    var currentBaudrate = -1
    var ptyThread: Thread? = null
    companion object {
        val usbPermissionRequestCode = 2212
    }

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

    private val printerProber by lazy { UsbSerialProber(getCustomPrinterProber()) }

    fun updateDevicesList(intent: String): String? {
        val availableDrivers = printerProber.findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            return null
        }

        val device = availableDrivers.first()
        return if (!usbManager.hasPermission(device!!.device)) {
            val mPendingIntent =
                PendingIntent.getBroadcast(context, usbPermissionRequestCode, Intent(intent), 0)
            usbManager.requestPermission(device.device, mPendingIntent)
            log { "REQUESTED DEVICE"}
            null
        } else {
            selectedDevice = device
            log { "GOT DEVICE"}
            device.device.deviceName
        }
    }

    override fun onDataReceived(data: SerialData?) {
        try {
            data?.apply {
                log { pty.getBaudrate(baudrate).toString() }
                if ((isStartPacket || currentBaudrate != baudrate) && selectedDevice != null) {
                    if (port?.isOpen == true) {
                        port?.close()
                    }
                    connection = usbManager.openDevice(selectedDevice?.device)
                    port = selectedDevice!!.ports.first()

                    port?.open(connection)
                    // @TODO get it from flags
                    port?.setParameters(
                        pty.getBaudrate(baudrate),
                        8,
                        UsbSerialPort.STOPBITS_2,
                        UsbSerialPort.PARITY_NONE
                    )
                    currentBaudrate = baudrate
                    port?.dtr = true
                    port?.rts = true

                    serialInputManager = SerialInputOutputManager(port, this@VirtualSerialDriver)
                    Executors.newSingleThreadExecutor().submit(serialInputManager)
                }

                if (data.data.size > 1) {
                    port?.write(data.serialData, 2000)
                }
            }
        } catch (e: Exception) {
            log { "Exception during write ${e.message}" }
        }
    }

    override fun onNewData(data: ByteArray) {
        pty.writeData(data)
    }

    override fun onRunError(e: Exception?) {
    }
}