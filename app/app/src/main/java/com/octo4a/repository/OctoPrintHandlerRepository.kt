package com.octo4a.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ServerStatus {
    InstallingBootstrap,
    DownloadingOctoPrint,
    InstallingDependencies,
    FirstBoot,
    Running,
    Stopped
}

interface OctoPrintHandlerRepository {
    val serverState: StateFlow<ServerStatus>
    fun beginInstallation()
}

class OctoPrintHandlerRepositoryImpl(
    val context: Context,
    private val bootstrapRepository: BootstrapRepository) : OctoPrintHandlerRepository {
    override val serverState: StateFlow<ServerStatus> = MutableStateFlow(ServerStatus.Stopped)

    override fun beginInstallation() {
//        if (!bootstrapRepository.isBootstrapInstalled) {
//
//        }
    }
}