package com.octo4a.repository

import android.content.Context
import com.octo4a.octoprint.BootstrapUtils
import com.octo4a.utils.isRunning
import com.octo4a.utils.log
import com.octo4a.utils.setPassword
import com.octo4a.utils.waitAndPrintOutput
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
}

class OctoPrintHandlerRepositoryImpl(
    val context: Context,
    private val bootstrapRepository: BootstrapRepository,
    private val githubRepository: GithubRepository) : OctoPrintHandlerRepository {
    private var _serverState = MutableStateFlow(ServerStatus.InstallingBootstrap)
    private var _octoPrintVersion = MutableStateFlow("...")
    private var octoPrintProcess: Process? = null

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
            }
            log { "Dependencies installed" }
            _serverState.emit(ServerStatus.BootingUp)
            startOctoPrint()
        } else {
            startOctoPrint()
            enableSSH()
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

    override fun stopOctoPrint() {
        octoPrintProcess?.destroy()
        _serverState.value = ServerStatus.Stopped
    }

    fun enableSSH() {
        bootstrapRepository.ensureHomeDirectory()
        // Generate ssh keys
        bootstrapRepository.runBashCommand("ssh-keygen -A -N \'\'").waitAndPrintOutput()
        // Sets password to `octoprint` @TODO Implement it properly
        bootstrapRepository.runBashCommand("passwd").setPassword()
        // Launches lemon demon
        bootstrapRepository.runBashCommand("sshd").waitAndPrintOutput()
    }
}