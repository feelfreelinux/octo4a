package com.octo4a.octoprint

import android.hardware.usb.UsbManager
import android.util.Log
import com.octo4a.serial.VirtualSerialDriver
import com.octo4a.utils.waitAndPrintOutput
import org.json.JSONObject

enum class OctoPrintEvent {
    START_SERVER,
    STOP_SERVER,
    BEGIN_INSTALLATION,
}

class OctoPrintManager(private val serialDriver: VirtualSerialDriver) {
    var octoPrintProcess: Process? = null

    fun startup() {
        octoPrintProcess = BootstrapUtils.runBashCommand("octoprint")

        // Startup ssh by default
        startSSH()
        serialDriver.initializeVSP()
        serialDriver.handlePtyThread()

        // Start thread that handles octoprint's output
        Thread {
            val reader = octoPrintProcess!!.inputStream.reader()

            reader.forEachLine {
                // TODO: Perhaps find a better way to handle it. Maybe some through plugin?
                Log.v("OCTO", it)
            }
        }.start()
    }


    fun startSSH() {
        BootstrapUtils.runBashCommand("sshd").waitAndPrintOutput()
    }

    fun handleEvent(event: OctoPrintEvent, data: JSONObject) {

    }
}