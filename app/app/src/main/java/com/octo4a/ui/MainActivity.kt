package com.octo4a.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.octo4a.R
import com.octo4a.camera.CameraService
import com.octo4a.octoprint.OctoPrintService
import com.octo4a.serial.VSPPty
import com.octo4a.utils.isServiceRunning
import kotlinx.android.synthetic.main.activity_installation_progress.*


class MainActivity : AppCompatActivity() {
    val vspPty by lazy { VSPPty() }
    var value = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        setContentView(R.layout.activity_installation_progress)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                value++
                progressTextView.text = value.toString() + "%"
                if (value == 5) {
                    bootstrapItem.isLoading = false
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
//        startOctoService()
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

//    @Composable
//    fun Greeting(name: String) {
//        Text (text = "Hello $name!")
//    }
//
//    @Preview(name = "MyPreviewName")
//    @Composable
//    fun TextDemo2(){
//        Greeting("Hello")
//    }

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