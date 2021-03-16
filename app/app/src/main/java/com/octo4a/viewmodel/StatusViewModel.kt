package com.octo4a.viewmodel

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.octo4a.Octo4aApplication
import com.octo4a.repository.OctoPrintHandlerRepository

class StatusViewModel(context: Application, private val octoPrintHandlerRepository: OctoPrintHandlerRepository) : AndroidViewModel(context) {
    val serverStatus = octoPrintHandlerRepository.serverState.asLiveData()
    val usbStatus = octoPrintHandlerRepository.usbDeviceStatus.asLiveData()
    val cameraStatus = octoPrintHandlerRepository.cameraServerStatus.asLiveData()

    private val ipAddress: String
        get() {
            val wm = getApplication<Octo4aApplication>().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        }

    fun startServer() {
        octoPrintHandlerRepository.startOctoPrint()
    }

    private fun getServerPort(): String {
        return octoPrintHandlerRepository.getConfigValue("server.port")
    }

    fun getServerAddress(): String {
        return "$ipAddress:${getServerPort()}"
    }

    fun stopServer() {
        octoPrintHandlerRepository.stopOctoPrint()
    }
}