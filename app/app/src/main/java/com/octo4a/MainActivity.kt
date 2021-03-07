package com.octo4a

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.octo4a.camera.CameraService
import com.octo4a.octoprint.OctoPrintService
import com.octo4a.serial.VSPPty
import com.octo4a.utils.isServiceRunning


class MainActivity : AppCompatActivity() {
    val vspPty by lazy { VSPPty() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startOctoService()
//        Thread {
//            vspPty.setVSPListener(this)
//        }.start()
//
//        if (!BootstrapUtils.isBootstrapInstalled) {
//            Thread {
//                BootstrapUtils.setupBootstrap {
//                    Log.v("Corn", "Bonk")
//                }
//            }.start()
//
//        } else {
//            Thread {
//                Log.v("Corn't", "Bonk")
//                BootstrapUtils.ensureHomeDirectory()
//////              BootstrapUtils.runBashCommand("ssh-keygen -A -N \'\'").waitAndPrintOutput()
////                val process = BootstrapUtils.runBashCommand("passwd")
////
////                process.inputStream.reader().forEachLine {
////                    Log.v("ASD", it)
////                    if (it.contains("New password:")) {
////                        process.outputStream.write("octo4a\n".toByteArray())
////                    }
////                    if (it.contains("Retype new password:")) {
////                        process.outputStream.write("octo4a\n".toByteArray())
////                    }
////                }
//
//                BootstrapUtils.runBashCommand("sshd").waitAndPrintOutput()
//            }.start()
//
//        }
    }

    private fun startOctoService() {
        if (!isServiceRunning(OctoPrintService::class.java)) {
            val i = Intent(this, OctoPrintService::class.java)
            startService(i)
        }

        if (!isServiceRunning(CameraService::class.java)) {
            val i = Intent(this, CameraService::class.java)
            startService(i)
        }

    }
}