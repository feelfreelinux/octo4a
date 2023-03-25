package com.octo4a.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.octo4a.repository.OctoPrintHandlerRepository

class InstallationViewModel(private val octoPrintHandlerRepository: OctoPrintHandlerRepository) : ViewModel() {
    val serverStatus = octoPrintHandlerRepository.serverState.asLiveData()
    val installErrorDescription = octoPrintHandlerRepository.installErrorDescription.asLiveData()
}