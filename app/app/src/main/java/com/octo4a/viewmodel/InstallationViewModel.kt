package com.octo4a.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.octo4a.repository.BootstrapRepository
import com.octo4a.repository.GithubRelease
import com.octo4a.repository.GithubRepository
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.utils.getArchString
import com.octo4a.utils.withIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class BootstrapItem(val title: String, val bootstrapVersion: String, val assetUrl: String, val prerelease: Boolean, var recommended: Boolean = false)
class InstallationViewModel(context: Application, octoPrintHandlerRepository: OctoPrintHandlerRepository, private val githubRepository: GithubRepository, private val bootstrapRepository: BootstrapRepository) : AndroidViewModel(context) {
    private var _bootstrapReleases = MutableStateFlow(listOf<BootstrapItem>())
    private var _selectedRelease = MutableStateFlow<BootstrapItem?>(null)

    val serverStatus = octoPrintHandlerRepository.serverState.asLiveData()
    val installErrorDescription = octoPrintHandlerRepository.installErrorDescription.asLiveData()
    val bootstrapReleases = _bootstrapReleases.asLiveData()
    val bootstrapDownloadProgress = bootstrapRepository.downloadProgressData

    fun fetchBootstrapReleases() {
        viewModelScope.launch {
            withIO {
                try {
                    // Fetch releases from octo4a-bootstrap-builder
                    val newestReleases = githubRepository.getNewestReleases("feelfreelinux/octo4a-bootstrap-builder").toMutableList()

                    // Sort for newest releases
                    newestReleases.sortByDescending { it.publishedAt }

                    val arch = getArchString()

                    val bootstrapItems = newestReleases.mapNotNull {
                        // Get matching asset
                        val asset = it.assets.firstOrNull { asset -> asset.name.contains(arch) }
                        val nameSplit = it.name.split("-")

                        // Filter out invalid releases
                        if (nameSplit.size != 2 || (asset == null)) null
                        else BootstrapItem("OctoPrint ${nameSplit[1]}", nameSplit.first().removePrefix("v"), asset.browserDownloadUrl, it.prerelease)
                    }


                    // Latest non-prerelease should be the recommended bootstrap
                    val recommendedItem = bootstrapItems.firstOrNull { !it.prerelease }

                    recommendedItem?.recommended = true
                    _bootstrapReleases.value = bootstrapItems
                    _selectedRelease.value = recommendedItem

                    recommendedItem?.apply {
                        bootstrapRepository.selectReleaseForInstallation(this)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun selectBootstrapRelease(bootstrapItem: BootstrapItem) {
        _selectedRelease.value = bootstrapItem
        bootstrapRepository.selectReleaseForInstallation(bootstrapItem)
    }
}