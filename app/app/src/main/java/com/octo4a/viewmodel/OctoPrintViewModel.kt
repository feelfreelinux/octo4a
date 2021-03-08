package com.octo4a.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel


class OctoPrintViewModel : ViewModel() {
    val serverStatus = MutableLiveData<ServerStatus>()

    init {
        serverStatus.value = ServerStatus.InstallingBootstrap
    }
}