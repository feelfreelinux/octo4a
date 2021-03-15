package com.octo4a.repository

import android.content.Context
import com.octo4a.octoprint.BootstrapUtils
import com.octo4a.utils.*
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

enum class ServerStatus(val value: Int) {
    InstallingBootstrap(0),
    DownloadingOctoPrint(1),
    InstallingDependencies(2),
    BootingUp(3),
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
    val octoPrintVersion: StateFlow<String>

    suspend fun beginInstallation()
    fun startOctoPrint()
    fun stopOctoPrint()
    fun startSSH()
    fun stopSSH()
    fun resetSSHPassword(password: String)
    fun getConfigValue(value: String): String
    val isSSHConfigured: Boolean
}

class OctoPrintHandlerRepositoryImpl(
    val context: Context,
    private val preferences: MainPreferences,
    private val bootstrapRepository: BootstrapRepository,
    private val githubRepository: GithubRepository) : OctoPrintHandlerRepository {
    private var _serverState = MutableStateFlow(ServerStatus.InstallingBootstrap)
    private var _octoPrintVersion = MutableStateFlow("...")
    private var octoPrintProcess: Process? = null
    override val isSSHConfigured: Boolean
        get() = bootstrapRepository.isSSHConfigured

    override val serverState: StateFlow<ServerStatus> = _serverState
    override val octoPrintVersion: StateFlow<String> = _octoPrintVersion

    override suspend fun beginInstallation() {
        if (!bootstrapRepository.isBootstrapInstalled) {
            val octoPrintRelease = githubRepository.getNewestRelease("OctoPrint/OctoPrint")
            _octoPrintVersion.emit(octoPrintRelease.tagName)

            log { "No bootstrap detected, proceeding with installation" }
            _serverState.emit(ServerStatus.InstallingBootstrap)
            bootstrapRepository.apply {
                setupBootstrap()
                ensureHomeDirectory()
            }
            log { "Bootstrap installed" }
            _serverState.emit(ServerStatus.DownloadingOctoPrint)
            bootstrapRepository.apply {
                runBashCommand("curl -o octoprint.zip -L ${octoPrintRelease.zipballUrl}").waitAndPrintOutput()
                runBashCommand("unzip octoprint.zip").waitAndPrintOutput()
            }
            _serverState.emit(ServerStatus.InstallingDependencies)
            bootstrapRepository.apply {
                runBashCommand("python3 -m ensurepip").waitAndPrintOutput()
                runBashCommand("cd Octo* && pip3 install .").waitAndPrintOutput()
                runBashCommand("ssh-keygen -A -N \'\'").waitAndPrintOutput()
            }
            log { "Dependencies installed" }
            _serverState.emit(ServerStatus.BootingUp)
            startOctoPrint()
        } else {
            startOctoPrint()
            if (preferences.enableSSH) {
                startSSH()
            }
        }
    }

    override fun startOctoPrint() {
        if (octoPrintProcess != null && octoPrintProcess!!.isRunning()) {
            return
        }
        _serverState.value = ServerStatus.BootingUp
        octoPrintProcess = BootstrapUtils.runBashCommand("octoprint")
        Thread {
            try {
                octoPrintProcess!!.inputStream.reader().forEachLine {
                    log { "octoprint: $it"}

                    // TODO: Perhaps find a better way to handle it. Maybe some through plugin?
                    if (it.contains("Listening on")) {
                        _serverState.value = ServerStatus.Running
                    }
                }
            } catch (e: Throwable) {
                _serverState.value = ServerStatus.Stopped
            }
        }.start()
    }

    override fun getConfigValue(value: String): String {
        return bootstrapRepository.runBashCommand("octoprint config get $value")
            .getOutputAsString()
            .replace("\n", "")
            .removeSurrounding("'")
    }

    override fun stopOctoPrint() {
        octoPrintProcess?.destroy()
        _serverState.value = ServerStatus.Stopped
    }

    override fun resetSSHPassword(password: String) {
        bootstrapRepository.resetSSHPassword(password)
    }

    override fun startSSH() {
        stopSSH()
        bootstrapRepository.runBashCommand("sshd").waitAndPrintOutput()
    }

    override fun stopSSH() {
        bootstrapRepository.runBashCommand("pkill sshd").waitAndPrintOutput()
    }
}