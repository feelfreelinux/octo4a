package com.octo4a.repository

import android.content.Context
import com.octo4a.utils.*
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import kotlin.math.roundToInt

enum class ServerStatus(val value: Int) {
    InstallingBootstrap(0),
    DownloadingOctoPrint(1),
    InstallingDependencies(2),
    BootingUp(3),
    Running(4),
    Stopped(5)
}

data class UsbDeviceStatus(val isAttached: Boolean, val port: String = "")

fun ServerStatus.getInstallationProgress(): Int {
    return ((value.toDouble() / 4) * 100).roundToInt()
}

fun ServerStatus.isInstallationFinished(): Boolean {
    return value == ServerStatus.Running.value
}

interface OctoPrintHandlerRepository {
    val serverState: StateFlow<ServerStatus>
    val octoPrintVersion: StateFlow<String>
    val usbDeviceStatus: StateFlow<UsbDeviceStatus>
    val cameraServerStatus: StateFlow<Boolean>

    suspend fun beginInstallation()
    fun startOctoPrint()
    fun stopOctoPrint()
    fun startSSH()
    fun stopSSH()
    fun usbAttached(port: String)
    fun usbDetached()
    fun resetSSHPassword(password: String)
    fun getConfigValue(value: String): String
    val isSSHConfigured: Boolean
    var isCameraServerRunning: Boolean
}

class OctoPrintHandlerRepositoryImpl(
    val context: Context,
    private val preferences: MainPreferences,
    private val bootstrapRepository: BootstrapRepository,
    private val githubRepository: GithubRepository,
    private val fifoEventRepository: FIFOEventRepository) : OctoPrintHandlerRepository {
    private val configFile by lazy {
        File("/data/data/com.octo4a/files/home/.octoprint/config.yaml")
    }
    private val yaml by lazy { Yaml() }

    private var _serverState = MutableStateFlow(ServerStatus.InstallingBootstrap)
    private var _octoPrintVersion = MutableStateFlow("...")
    private var _usbDeviceStatus = MutableStateFlow(UsbDeviceStatus(false))
    private var _cameraServerStatus = MutableStateFlow(false)

    private var octoPrintProcess: Process? = null
    private var fifoThread: Thread? = null
    override val isSSHConfigured: Boolean
        get() = bootstrapRepository.isSSHConfigured

    override val serverState: StateFlow<ServerStatus> = _serverState
    override val octoPrintVersion: StateFlow<String> = _octoPrintVersion
    override val usbDeviceStatus: StateFlow<UsbDeviceStatus> = _usbDeviceStatus
    override val cameraServerStatus: StateFlow<Boolean> = _cameraServerStatus

    override var isCameraServerRunning: Boolean
        get() = _cameraServerStatus.value
        set(value) {
            _cameraServerStatus.value = value
        }

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
            insertInitialConfig()
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
        bootstrapRepository.runBashCommand("mkfifo eventPipe").waitAndPrintOutput()
        _serverState.value = ServerStatus.BootingUp
        octoPrintProcess = bootstrapRepository.runBashCommand("octoprint")
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
        if (fifoThread?.isAlive != true) {
            fifoThread = Thread {
                fifoEventRepository.handleFifoEvents()
            }
            fifoThread?.start()
        }
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
        // Kills ssh demon
        bootstrapRepository.runBashCommand("pkill sshd").waitAndPrintOutput()
    }

    override fun usbAttached(port: String) {
        _usbDeviceStatus.value = UsbDeviceStatus(true, port)
    }

    override fun usbDetached() {
        _usbDeviceStatus.value = UsbDeviceStatus(false)
    }

    private fun insertInitialConfig() {
        bootstrapRepository.ensureHomeDirectory()
        bootstrapRepository.runBashCommand("mkdir -p /data/data/com.octo4a/files/home/.octoprint")
        val map = getConfig()
        map["webcam"] = mapOf(
            "stream" to "http://${context.ipAddress}:5001/mjpeg",
            "ffmpeg" to "/data/data/com.octo4a/files/usr/bin/ffmpeg",
            "snapshot" to "http://localhost:5001/snapshot"
        )
        map["serial"] = mapOf(
            "exclusive" to false,
            "additionalPorts" to listOf("/data/data/com.octo4a/files/home/serialpipe"),
            "blacklistedPorts" to listOf("/dev/*")
            )
        map["server"] = mapOf("commands" to mapOf(
            "serverRestartCommand" to "echo \"{\\\"eventType\\\": \\\"restartServer\\\"}\" > eventPipe",
            "systemShutdownCommand" to "echo \"{\\\"eventType\\\": \\\"stopServer\\\"}\" > eventPipe"
        ))

        saveConfig(map)
    }

    private fun getConfig(): MutableMap<String, Any> { var output = emptyMap<String, Any>()
        if (configFile.exists()) {
            output = yaml.load(configFile.inputStream()) as Map<String, Any>
        } else {
            val file = File("/data/data/com.octo4a/files/home/.octoprint")
            file.mkdirs()
            configFile.createNewFile()
        }

        return output.toMutableMap()
    }

    fun saveConfig(config: MutableMap<String, Any>) {
        val writer = FileWriter(configFile, false)

        yaml.dump(config, writer)
        writer.flush()
        writer.close()

        val backupFile = File("/data/data/com.octo4a/files/home/.octoprint/config.backup")
        backupFile.delete()
        bootstrapRepository.runBashCommand("cp .octoprint/config.yaml .octoprint/config.backup")
    }
}