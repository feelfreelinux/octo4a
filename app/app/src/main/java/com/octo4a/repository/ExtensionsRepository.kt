package com.octo4a.repository

import com.google.gson.GsonBuilder
import org.json.JSONArray
import android.content.Context
import android.os.Handler
import com.octo4a.utils.getOutputAsString
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.lang.Exception

enum class ExtensionStatus {
    Running,
    Stopped,
    Stopping,
    Crashed
}

data class RegisteredExtension(val name: String, val title: String, val description: String, var status: ExtensionStatus = ExtensionStatus.Stopped)

data class ExtensionSettings(val name: String, var enabled: Boolean)

interface ExtensionsRepository {
    val extensionsState: StateFlow<List<RegisteredExtension>>

    fun startUpNecessaryExtensions()
    fun stopExtension(extension: RegisteredExtension, killedCallback: () -> Unit = {})
    fun modifyExtensionSetting(name: String, enabled: Boolean)
    fun getExtensionSettings(name: String): ExtensionSettings?
    fun startExtension(extension: RegisteredExtension)
    fun stopAllExtensions()

}

class ExtensionsRepositoryImpl(
    val context: Context,
    private val preferences: MainPreferences,
    private val logger: LoggerRepository,
    private val bootstrapRepository: BootstrapRepository) : ExtensionsRepository {
    private val extensionsPath by lazy { context.getExternalFilesDir(null).absolutePath + "/extensions" }
    private val extensionMap = mutableMapOf<String, Thread?>()
    val gson by lazy {
        GsonBuilder().create()
    }

    private val _extensionsState = MutableStateFlow<List<RegisteredExtension>>(listOf())

    override val extensionsState = _extensionsState

    override fun startUpNecessaryExtensions() {
        kotlin.runCatching {
            getValidExtensions()

            getExtensionSettings().forEach { extension ->
                if (extension.enabled) {
                    extensionsState.value.firstOrNull { it.name == extension.name }?.apply {
                        startExtension(this)
                    }
                }
            }
        }.onFailure {

        }
    }

    override fun stopAllExtensions() {
        getExtensionSettings().forEach { extension ->
            if (extension.enabled) {
                extensionsState.value.firstOrNull { it.name == extension.name }?.apply {
                    stopExtension(this)
                }
            }
        }
    }

    override fun stopExtension(extension: RegisteredExtension, killedCallback: () -> Unit) {
        logger.log(null, LogType.EXTENSION) { "Trying to stop extension ${extension.name}" }
        if (extensionMap.contains(extension.name)) {
            when (extension.status) {
                ExtensionStatus.Running -> {
                    if (File("${extensionsPath}/${extension.name}/kill.sh").exists()) {
                        extension.status = ExtensionStatus.Stopping
                        updateExtension(extension)

                        Thread {
                            val process =
                                bootstrapRepository.runCommand("/root/extensions/${extension.name}/kill.sh")

                            connectReader(extension, process)
                            process.waitFor()
                            extension.status = ExtensionStatus.Stopped
                            updateExtension(extension)
                            killedCallback()
                        }.start()
                    } else {
                        extensionMap[extension.name]?.interrupt()
                        extensionMap[extension.name] = null
                        killedCallback()
                    }
                }

                ExtensionStatus.Crashed -> {
                    extensionMap[extension.name]?.interrupt()
                    extensionMap[extension.name] = null
                    killedCallback()
                }

                else -> killedCallback()
            }
        } else {
            killedCallback()
        }
    }

    override fun startExtension(extension: RegisteredExtension) {
        logger.log(null, LogType.EXTENSION) { "Trying to start extension ${extension.name}" }
        stopExtension(extension) {
            extension.status = ExtensionStatus.Running
            updateExtension(extension)

            extensionMap[extension.name] = Thread {
                val process =
                    bootstrapRepository.runCommand("sh /root/extensions/${extension.name}/start.sh", root = true)
                connectReader(extension, process)

                try {
                    if (process.waitFor() == 0) {
                        extension.status = ExtensionStatus.Stopped
                    } else {
                        extension.status = ExtensionStatus.Crashed
                    }
                } catch (e: Exception) {
                    if (e is InterruptedException) {
                        extension.status = ExtensionStatus.Stopped
                    } else {
                        extension.status = ExtensionStatus.Crashed
                    }
                }

                updateExtension(extension)
                extensionMap.remove(extension.name)
            }

            extensionMap[extension.name]?.start()
        }
    }

    override fun getExtensionSettings(name: String): ExtensionSettings? {
        return getExtensionSettings().firstOrNull { it.name == name }
    }

    private fun connectReader(extension: RegisteredExtension, process: Process) {
        process.inputStream.bufferedReader().forEachLine {
            logger.log(null, LogType.EXTENSION) { "[${extension.name}]: $it" }
        }
    }

    private fun updateExtension(extension: RegisteredExtension) {
        Handler(context.mainLooper).post {
            val extensions = _extensionsState.value
            extensions.forEach {
                if (it.name == extension.name) {
                    it.status = extension.status
                }
            }

            _extensionsState.tryEmit(listOf())
            extensions.forEach {
                logger.log(null, LogType.EXTENSION) { it.status.toString() }
            }
            _extensionsState.tryEmit(extensions)
        }
    }

    override fun modifyExtensionSetting(name: String, enabled: Boolean) {
        if (!enabled) {
            _extensionsState.value.firstOrNull { it.name == name }?.apply {
                stopExtension(this)
            }
        }

        val settings = getExtensionSettings().toMutableList()
        val setting = settings.firstOrNull { it.name == name }
        if (setting == null) settings.add(ExtensionSettings(name, enabled))
        else {
            setting.enabled = enabled
        }
        preferences.extensionSettings = gson.toJson(settings)
        startUpNecessaryExtensions()
    }

    fun getExtensionSettings(): List<ExtensionSettings> {
        val settings = preferences.extensionSettings
        val parsed = JSONArray(settings ?: "[]")
        val settingResult = mutableListOf<ExtensionSettings>()
        for (i in 0 until parsed.length()) {
            val extensionObject = parsed.getJSONObject(i)
            settingResult.add(ExtensionSettings(extensionObject.getString("name"), extensionObject.getBoolean("enabled")))
        }

        return settingResult
    }

    private fun getValidExtensions() {
        val validExtensions = mutableListOf<RegisteredExtension>()

        val f = File(extensionsPath)
        val files = f.listFiles()
        for (inFile in files) {
            if (inFile.isDirectory) {
                try {
                    val manifestFile = File(inFile.path + "/manifest.json")
                    val startFile = File(inFile.path + "/start.sh")
                    if (manifestFile.isFile && startFile.isFile) {
                        val jsonObject = JSONObject(manifestFile.readText())
                        val title = jsonObject.get("title") as String
                        val description = jsonObject.get("description") as String
                        validExtensions.add(
                            RegisteredExtension(
                                inFile.name,
                                title,
                                description
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Invalid extension
                    logger.log(null, LogType.EXTENSION) { "Failed to load extension: " + inFile.name }
                }
            }
        }

        validExtensions.forEach {
            logger.log(null, LogType.EXTENSION) { "Got extension " + it.title }
        }

        _extensionsState.value = validExtensions
    }
}