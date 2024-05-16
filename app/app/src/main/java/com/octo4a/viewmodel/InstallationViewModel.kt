package com.octo4a.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.octo4a.repository.BootstrapRepository
import com.octo4a.repository.GithubRelease
import com.octo4a.repository.GithubRepository
import com.octo4a.repository.OctoPrintHandlerRepository
import com.octo4a.utils.withIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class InstallationViewModel(context: Application, private val octoPrintHandlerRepository: OctoPrintHandlerRepository, private val githubRepository: GithubRepository, private val bootstrapRepository: BootstrapRepository) : AndroidViewModel(context) {
    private var _bootstrapReleases = MutableStateFlow(listOf<GithubRelease>())
    private var _selectedGitHubRelease = MutableStateFlow<GithubRelease?>(null)

    val serverStatus = octoPrintHandlerRepository.serverState.asLiveData()
    val installErrorDescription = octoPrintHandlerRepository.installErrorDescription.asLiveData()
    val bootstrapReleases = _bootstrapReleases.asLiveData()

    fun fetchBootstrapReleases() {
        viewModelScope.launch {
            withIO {
                try {
                    // Fetch releases from octo4a-bootstrap-builder
                    val newestReleases = githubRepository.getNewestReleases("feelfreelinux/octo4a-bootstrap-builder")
                    _bootstrapReleases.value = newestReleases

                    bootstrapRepository.selectReleaseForInstallation(newestReleases.first())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun selectBootstrapRelease(releaseStr: String) {
        val release = _bootstrapReleases.value.firstOrNull { releaseStr.contains(it.name) }

        release?.apply {
            _selectedGitHubRelease.value = this
            bootstrapRepository.selectReleaseForInstallation(this)
        }
    }
}