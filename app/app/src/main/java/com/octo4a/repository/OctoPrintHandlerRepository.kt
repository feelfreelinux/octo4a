package com.octo4a.repository

import android.R.attr.path
import android.R.attr.start
import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.JsonParser
import com.octo4a.serial.VSPPty
import com.octo4a.serial.VirtualSerialDriver
import com.octo4a.utils.*
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import java.lang.Exception
import java.lang.reflect.Field
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.roundToInt


enum class ServerStatus(val value: Int, val progress: Boolean=false) {
    InstallingBootstrap(0),
    DownloadingOctoPrint(1),
    InstallingDependencies(2),
    BootingUp(3, true),
    Running(4),
    ShuttingDown(3, true),
    Stopped(5),
    Corrupted(6)
}

enum class ExtrasStatus {
    NotInstalled,
    Installing,
    Installed
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
    val registeredExtensions: StateFlow<List<RegisteredExtension>>
    val cameraServerStatus: StateFlow<Boolean>
    val extrasStatus: StateFlow<ExtrasStatus>

    suspend fun beginInstallation()
    fun startOctoPrint()
    fun stopOctoPrint()
    fun startSSH()
    fun stopSSH()
    fun installExtras()
    fun usbAttached(port: String)
    fun getExtrasStatus()
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
    private val extensionsRepository: ExtensionsRepository,
    private val fifoEventRepository: FIFOEventRepository) : OctoPrintHandlerRepository {
    private val externalStorageSymlinkPath = Environment.getExternalStorageDirectory().path + "/OctoPrint"
    private val octoPrintStoragePath = "${context.getExternalFilesDir(null).absolutePath}/.octoprint"
    private val configFile by lazy {
        File("$octoPrintStoragePath/config.yaml")
    }

    private val commPyFixFile by lazy {
        File("$octoPrintStoragePath/plugins/comm-fix.py")
    }
    private val vspPty by lazy { VSPPty() }
    private val yaml by lazy { Yaml() }

    private var _serverState = MutableStateFlow(ServerStatus.InstallingBootstrap)
    private var _extensionsState = MutableStateFlow(listOf<RegisteredExtension>())
    private var _octoPrintVersion = MutableStateFlow("...")
    private var _usbDeviceStatus = MutableStateFlow(UsbDeviceStatus(false))
    private var _cameraServerStatus = MutableStateFlow(false)
    private var _extrasStatus = MutableStateFlow(ExtrasStatus.NotInstalled)
    private var wakeLock = Octo4aWakeLock(context, logger)

    private var octoPrintProcess: Process? = null
    private var fifoThread: Thread? = null

    override val serverState: StateFlow<ServerStatus> = _serverState
    override val registeredExtensions: StateFlow<List<RegisteredExtension>> = _extensionsState
    override val octoPrintVersion: StateFlow<String> = _octoPrintVersion
    override val usbDeviceStatus: StateFlow<UsbDeviceStatus> = _usbDeviceStatus
    override val cameraServerStatus: StateFlow<Boolean> = _cameraServerStatus
    override val extrasStatus: StateFlow<ExtrasStatus> = _extrasStatus

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
                    runCommand("curl -s https://raw.githubusercontent.com/feelfreelinux/octo4a/master/scripts/setup-octo4a.sh | bash -s").waitAndPrintOutput(
                        logger
                    )
                    runCommand("curl -o octoprint.zip -L ${octoPrintRelease.zipballUrl}").waitAndPrintOutput(logger)
                    runCommand("unzip octoprint.zip").waitAndPrintOutput(logger)
                }
                _serverState.emit(ServerStatus.InstallingDependencies)
                bootstrapRepository.apply {
                    runCommand("cd Octo* && pip3 install .").waitAndPrintOutput(logger)
                }
                _serverState.emit(ServerStatus.BootingUp)
                insertInitialConfig()
                vspPty.cancelPtyThread()
                Thread.sleep(10)
                vspPty.runPtyThread()
                startOctoPrint()
                logger.log { "Dependencies installed" }
            } else {
                getExtrasStatus()
                startOctoPrint()
                if (preferences.enableSSH) {
                    logger.log { "Enabling ssh" }
                    startSSH()
                }
                extensionsRepository.startUpNecessaryExtensions()
            }
        }
    }

    override fun getExtrasStatus() {
        val file = File("/data/data/com.octo4a/files/bootstrap/bootstrap/usr/bin/gcc")

        if(file.exists()) {
            _extrasStatus.value = ExtrasStatus.Installed
        } else if (_extrasStatus.value != ExtrasStatus.Installing) {
            _extrasStatus.value = ExtrasStatus.NotInstalled
        }
    }

    override fun installExtras() {
        if (_extrasStatus.value == ExtrasStatus.NotInstalled) {
            Thread {
                _extrasStatus.value = ExtrasStatus.Installing
                bootstrapRepository.runCommand("curl -s https://raw.githubusercontent.com/feelfreelinux/octo4a/master/scripts/setup-plugin-extras.sh | bash -s")
                    .waitAndPrintOutput(
                        logger
                    )
                _extrasStatus.value = ExtrasStatus.Installed
            }.start()
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

    fun ensureCommFixExists() {
        if (!commPyFixFile.exists()) {
            // OctoPrint 1.8.0 breaks android compatibility, force install a plugin that monkey-fixes comm.py
            bootstrapRepository.runCommand("mkdir -p ~/.octoprint/plugins; curl -o ~/.octoprint/plugins/comm-fix.py -L https://raw.githubusercontent.com/feelfreelinux/octo4a/master/scripts/comm-fix.py").waitAndPrintOutput(
                logger
            )
        }
    }

    override fun startOctoPrint() {
        if (!isInstalledProperly) {
            _serverState.value = ServerStatus.Corrupted
            return
        }
        wakeLock.acquire()

        if (octoPrintProcess != null && octoPrintProcess!!.isRunning()) {
            logger.log { "Failed to start. OctoPrint already running." }
        }
        bootstrapRepository.run {
            vspPty.createEventPipe()
        }
        ensureCommFixExists()
        _serverState.value = ServerStatus.BootingUp
        octoPrintProcess = bootstrapRepository.runCommand("LD_PRELOAD=/home/octoprint/ioctlHook.so octoprint serve --iknowwhatimdoing")
        Thread {
            try {
                octoPrintProcess!!.inputStream.reader().forEachLine {
                    logger.log(this, LogType.OCTOPRINT) { it }

                    // TODO: Perhaps find a better way to handle it. Maybe some through plugin?
                    if (it.contains("Listening on")) {
                        _serverState.value = ServerStatus.Running
                    }
                }
            } catch (e: Throwable) {
                stopOctoPrint()
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
        _serverState.value = ServerStatus.ShuttingDown
        Thread {
            while(!Thread.interrupted()) {
                Thread.sleep(500)

                if (octoPrintProcess?.isRunning() == false) {
                    _serverState.value = ServerStatus.Stopped
                    break;
                }
            }
        }.start()
    }

    override fun resetSSHPassword(password: String) {
        bootstrapRepository.runCommand("echo \"$password\" > /root/.octoCredentials")
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
            "ffmpeg" to "/usr/bin/ffmpeg",
            "snapshot" to "http://localhost:5001/snapshot"
        )
        map["serial"] = mapOf(
            "exclusive" to false,
            "additionalPorts" to listOf("/dev/ttyOcto4a")
            )
        map["server"] = mapOf("commands" to mapOf(
            "serverRestartCommand" to "echo \"{\\\"eventType\\\": \\\"restartServer\\\"}\" > /eventPipe",
            "systemShutdownCommand" to "echo \"{\\\"eventType\\\": \\\"stopServer\\\"}\" > /eventPipe"
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

    // Validate installation
    val isInstalledProperly: Boolean
        get() = bootstrapRepository.runCommand("command -v octoprint").getOutputAsString().contains("/usr/bin/octoprint")
}