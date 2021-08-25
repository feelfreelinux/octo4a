package com.octo4a.repository

import android.content.Context
import android.os.Environment
import com.bugsnag.android.Bugsnag
import com.octo4a.utils.*
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import java.lang.reflect.Field
import java.util.regex.Matcher
import java.util.regex.Pattern
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
    var isCameraServerRunning: Boolean
    val isSSHConfigured: Boolean
}

class OctoPrintHandlerRepositoryImpl(
    val context: Context,
    private val preferences: MainPreferences,
    private val logger: LoggerRepository,
    private val bootstrapRepository: BootstrapRepository,
    private val githubRepository: GithubRepository,
    private val fifoEventRepository: FIFOEventRepository) : OctoPrintHandlerRepository {
    private val externalStorageSymlinkPath = Environment.getExternalStorageDirectory().path + "/OctoPrint"
    private val octoPrintStoragePath = "/data/data/com.octo4a/files/bootstrap/bootstrap/home/octoprint/.octoprint"
    private val configFile by lazy {
        File("$octoPrintStoragePath/config.yaml")
    }
    private val yaml by lazy { Yaml() }

    private var _serverState = MutableStateFlow(ServerStatus.InstallingBootstrap)
    private var _octoPrintVersion = MutableStateFlow("...")
    private var _usbDeviceStatus = MutableStateFlow(UsbDeviceStatus(false))
    private var _cameraServerStatus = MutableStateFlow(false)
    private var wakeLock = Octo4aWakeLock(context, logger)

    private var octoPrintProcess: Process? = null
    private var fifoThread: Thread? = null


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
        withContext(Dispatchers.IO) {

            if (!bootstrapRepository.isBootstrapInstalled) {
                val octoPrintRelease = githubRepository.getNewestRelease("OctoPrint/OctoPrint")
                _octoPrintVersion.emit(octoPrintRelease.tagName)

                logger.log { "No bootstrap detected, proceeding with installation" }
                _serverState.emit(ServerStatus.InstallingBootstrap)
                bootstrapRepository.apply {
                    setupBootstrap()
                }
                logger.log { "Bootstrap installed" }
                _serverState.emit(ServerStatus.DownloadingOctoPrint)
                bootstrapRepository.apply {
                    runCommand("apk add curl py3-pip py3-yaml py3-regex py3-netifaces py3-psutil unzip").waitAndPrintOutput(
                        logger
                    )
                    runCommand("curl -o octoprint.zip -L ${octoPrintRelease.zipballUrl}").waitAndPrintOutput(logger)
                    runCommand("unzip octoprint.zip").waitAndPrintOutput(logger)
                }
                _serverState.emit(ServerStatus.InstallingDependencies)
                bootstrapRepository.apply {
                    runCommand("cd Octo* && pip3 install .").waitAndPrintOutput(logger)
                }
                logger.log { "Dependencies installed" }
                _serverState.emit(ServerStatus.BootingUp)
                insertInitialConfig()
                startOctoPrint()
            } else {
                startOctoPrint()
                if (preferences.enableSSH) {
                    logger.log { "Enabling ssh" }
                    startSSH()
                }
            }
        }
    }

    fun getPid(p: Process): Int {
        var pid = -1
        try {
            val f: Field = p.javaClass.getDeclaredField("pid")
            f.isAccessible = true
            pid = f.getInt(p)
            f.isAccessible = false
        } catch (ignored: Throwable) {
            pid = try {
                val m: Matcher = Pattern.compile("pid=(\\d+)").matcher(p.toString())
                if (m.find()) m.group(1).toInt() else -1
            } catch (ignored2: Throwable) {
                -1
            }
        }
        return pid
    }

    override fun startOctoPrint() {
        wakeLock.acquire()
        if (octoPrintProcess != null && octoPrintProcess!!.isRunning()) {
            logger.log { "Failed to start. OctoPrint already running." }
            return
        }
        bootstrapRepository.run {
            runCommand("mkfifo /home/octoprint/eventPipe").waitAndPrintOutput(logger)
        }
        _serverState.value = ServerStatus.BootingUp
        octoPrintProcess = bootstrapRepository.runCommand("LD_PRELOAD=/home/octoprint/ioctlHook.so octoprint", root = false)
        Thread {
            try {
                octoPrintProcess!!.inputStream.reader().forEachLine {
                    Bugsnag.leaveBreadcrumb(it)
                    logger.log(this, LogType.OCTOPRINT) { it }

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
        return bootstrapRepository.runCommand("octoprint config get $value", root = false)
            .getOutputAsString()
            .replace("\n", "")
            .removeSurrounding("'")
    }

    override fun stopOctoPrint() {
        wakeLock.remove()
        bootstrapRepository.runCommand("kill `pidof octoprint`")
        octoPrintProcess?.destroy()
        _serverState.value = ServerStatus.Stopped
    }

    override fun resetSSHPassword(password: String) {
        bootstrapRepository.resetSSHPassword(password)
    }

    override fun startSSH() {
        stopSSH()
        bootstrapRepository.runCommand("/usr/sbin/sshd -p ${preferences.sshPort}")
    }

    override fun stopSSH() {
        // Kills ssh demon
        bootstrapRepository.runCommand("pkill sshd").waitAndPrintOutput(logger)
        logger.log(this) { "killed sshd" }
    }

    override fun usbAttached(port: String) {
        _usbDeviceStatus.value = UsbDeviceStatus(true, port)
    }

    override fun usbDetached() {
        _usbDeviceStatus.value = UsbDeviceStatus(false)
    }

    private fun insertInitialConfig() {
        bootstrapRepository.ensureHomeDirectory()
        val map = getConfig()
        map["webcam"] = mapOf(
            "stream" to "http://${context.ipAddress}:5001/mjpeg",
            "ffmpeg" to "/data/data/com.octo4a/files/usr/bin/ffmpeg",
            "snapshot" to "http://localhost:5001/snapshot"
        )
        map["serial"] = mapOf(
            "exclusive" to false,
            "additionalPorts" to listOf("/dev/ttyOcto4a"),
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
            val file = File(octoPrintStoragePath)
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

        val backupFile = File("$octoPrintStoragePath/config.backup")
        backupFile.delete()
    }

    override val isSSHConfigured: Boolean
        get() = bootstrapRepository.isSSHConfigured
}