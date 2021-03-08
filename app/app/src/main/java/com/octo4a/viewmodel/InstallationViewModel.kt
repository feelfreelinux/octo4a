package com.octo4a.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.repository.ServerStatus
import com.octo4a.utils.withIO
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class InstallationViewModel(private val octoPrintHandlerRepository: OctoPrintHandlerRepository) : ViewModel() {
    val serverStatus = octoPrintHandlerRepository.serverState.asLiveData()
}