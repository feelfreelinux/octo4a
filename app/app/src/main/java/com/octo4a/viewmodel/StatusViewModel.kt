package com.octo4a.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.octo4a.Octo4aApplication
import com.octo4a.repository.GithubRepository
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.utils.ipAddress

class StatusViewModel(context: Application,
                      private val octoPrintHandlerRepository: OctoPrintHandlerRepository,
                      private val githubRepository: GithubRepository) : AndroidViewModel(context) {
    val serverStatus = octoPrintHandlerRepository.serverState.asLiveData()
    val usbStatus = octoPrintHandlerRepository.usbDeviceStatus.asLiveData()
    val cameraStatus = octoPrintHandlerRepository.cameraServerStatus.asLiveData()

    fun startServer() {
        octoPrintHandlerRepository.startOctoPrint()
    }

    private fun getServerPort(): String {
        return octoPrintHandlerRepository.getConfigValue("server.port")
    }

    fun getServerAddress(): String {
        return "${getApplication<Octo4aApplication>().applicationContext.ipAddress}:5000"
    }

    fun stopServer() {
        octoPrintHandlerRepository.stopOctoPrint()
    }
}