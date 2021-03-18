package com.octo4a.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.octo4a.R
import com.octo4a.serial.VirtualSerialDriver
import com.octo4a.service.OctoPrintService
import kotlinx.android.synthetic.main.activity_main.*
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    private val vsp: VirtualSerialDriver by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottomNavigationBar.setupWithNavController(findNavController(R.id.navHost))
        vsp.updateDevicesList(OctoPrintService.BROADCAST_SERVICE_USB_GOT_ACCESS)
    }

    // @TODO: refactor to non deprecated api
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VirtualSerialDriver.usbPermissionRequestCode) {
            vsp.updateDevicesList(OctoPrintService.BROADCAST_SERVICE_USB_GOT_ACCESS)
        }
    }
}