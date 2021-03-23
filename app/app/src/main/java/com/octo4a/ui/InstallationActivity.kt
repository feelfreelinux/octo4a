package com.octo4a.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.octo4a.R
import com.octo4a.service.OctoPrintService
import com.octo4a.repository.ServerStatus
import com.octo4a.repository.getInstallationProgress
import com.octo4a.ui.views.InstallationProgressItem
import com.octo4a.viewmodel.InstallationViewModel
import kotlinx.android.synthetic.main.activity_installation_progress.*
import org.koin.androidx.viewmodel.ext.android.viewModel

class InstallationActivity : AppCompatActivity() {
    private val installationViewModel: InstallationViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        setContentView(R.layout.activity_installation_progress)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        installationViewModel.serverStatus.observe(this) {
            progressTextView.text = "${it.getInstallationProgress()}%"
            setItemsState(it)
            continueButton.isEnabled = it == ServerStatus.Running
        }

        continueButton.setOnClickListener {
            stopService(Intent(this, OctoPrintService::class.java))
            val intent = Intent(this, InitialActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setItemsState(status: ServerStatus) {
        bootstrapItem.setStatus(status, ServerStatus.InstallingBootstrap)
        downloadingOctoprintItem.setStatus(status, ServerStatus.DownloadingOctoPrint)
        installingDependenciesItem.setStatus(status, ServerStatus.InstallingDependencies)
        bootingOctoprintItem.setStatus(status, ServerStatus.BootingUp)
        installationCompleteItem.setStatus(status, ServerStatus.Running)
    }

    private fun InstallationProgressItem.setStatus(currentStatus: ServerStatus, requiredStatus: ServerStatus) {
        status = requiredStatus
        isLoading = currentStatus.value <= requiredStatus.value
        if (currentStatus.value < requiredStatus.value) {
            setUpcoming()
        }

        if (currentStatus == ServerStatus.Running) {
            isLoading = false
        }
    }
}