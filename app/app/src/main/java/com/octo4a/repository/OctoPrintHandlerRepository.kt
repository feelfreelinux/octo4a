package com.octo4a.repository

import android.content.Context
import com.octo4a.octoprint.BootstrapUtils
import com.octo4a.utils.log
import com.octo4a.utils.waitAndPrintOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

enum class ServerStatus(val value: Int) {
    InstallingBootstrap(0),
    DownloadingOctoPrint(1),
    InstallingDependencies(2),
    FirstBoot(3),
    Running(4),
    Stopped(5)
}

fun ServerStatus.getInstallationProgress(): Int {
    return ((value.toDouble() / 4) * 100).roundToInt()
}

fun ServerStatus.isInstallationFinished(): Boolean {
    return value == ServerStatus.Running.value
}


interface OctoPrintHandlerRepository {
    val serverState: StateFlow<ServerStatus>
    suspend fun beginInstallation()
}

class OctoPrintHandlerRepositoryImpl(
    val context: Context,
    private val bootstrapRepository: BootstrapRepository) : OctoPrintHandlerRepository {
    private var _serverState = MutableStateFlow(ServerStatus.InstallingBootstrap)

    override val serverState: StateFlow<ServerStatus> = _serverState

    override suspend fun beginInstallation() {
        if (!bootstrapRepository.isBootstrapInstalled) {
            log { "No bootstrap detected, proceeding with installation" }

            _serverState.emit(ServerStatus.InstallingBootstrap)
            bootstrapRepository.setupBootstrap()
            log { "Bootstrap installed" }
            _serverState.emit(ServerStatus.DownloadingOctoPrint)
            Thread.sleep(3000)
            log { "OctoPrint downloaded" }
            _serverState.emit(ServerStatus.InstallingDependencies)
            Thread.sleep(3000)
            log { "Dependencies installed" }
            _serverState.emit(ServerStatus.FirstBoot)
            Thread.sleep(3000)
            log { "Booted up for the first time" }
            _serverState.emit(ServerStatus.Running)
        } else {
            log{"CORNED"}
            bootstrapRepository.ensureHomeDirectory()
            bootstrapRepository.runBashCommand("passwd").waitAndPrintOutput()
        }
    }
}