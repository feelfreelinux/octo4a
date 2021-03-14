package com.octo4a.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.octo4a.repository.OctoPrintHandlerRepository

class StatusViewModel(private val octoPrintHandlerRepository: OctoPrintHandlerRepository) : ViewModel() {
    val serverStatus = octoPrintHandlerRepository.serverState.asLiveData()

    fun startServer() {
        octoPrintHandlerRepository.startOctoPrint()
    }

    fun stopServer() {
        octoPrintHandlerRepository.stopOctoPrint()
    }
}