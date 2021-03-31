package com.octo4a.viewmodel

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.*
import com.octo4a.Octo4aApplication
import com.octo4a.repository.GithubRelease
import com.octo4a.repository.GithubRepository
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.utils.SemVer
import com.octo4a.utils.ipAddress
import com.octo4a.utils.withIO
import kotlinx.coroutines.launch
import java.lang.Exception


class StatusViewModel(context: Application,
                      private val octoPrintHandlerRepository: OctoPrintHandlerRepository,
                      private val githubRepository: GithubRepository) : AndroidViewModel(context) {
    val serverStatus = octoPrintHandlerRepository.serverState.asLiveData()
    val usbStatus = octoPrintHandlerRepository.usbDeviceStatus.asLiveData()
    val cameraStatus = octoPrintHandlerRepository.cameraServerStatus.asLiveData()
    val updateAvailable = MutableLiveData<GithubRelease>()

    fun startServer() {
        octoPrintHandlerRepository.startOctoPrint()
    }

    fun checkUpdateAvailable() {
        viewModelScope.launch {
            withIO {
                try {
                    val newestRelease = githubRepository.getNewestRelease("feelfreelinux/octo4a")

                    val app = getApplication<Octo4aApplication>()
                    val pInfo: PackageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
                    val version = pInfo.versionName
                    if (SemVer.parse(version) < SemVer.parse(newestRelease.tagName)) {
                        // New version available, check if is built already
                        if (newestRelease.assets.any { it.name.contains("bootstrap") }
                            && newestRelease.assets.any { it.name.contains(".apk") }) {
                            updateAvailable.postValue(newestRelease)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getServerPort(): String {
        return octoPrintHandlerRepository.getConfigValue("server.port")
    }

    fun getServerAddress(): String {
        return "${getApplication<Octo4aApplication>().applicationContext.ipAddress}:5000"
    }

    fun stopServer() {
        octoPrintHandlerRepository.stopOctoPrint()
    }
}