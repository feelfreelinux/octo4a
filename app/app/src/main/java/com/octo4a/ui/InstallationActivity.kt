package com.octo4a.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.octo4a.R
import com.octo4a.service.OctoPrintService
import com.octo4a.repository.ServerStatus
import com.octo4a.repository.getInstallationProgress
import com.octo4a.ui.fragments.TerminalSheetDialog
import com.octo4a.ui.views.InstallationProgressItem
import com.octo4a.utils.preferences.MainPreferences
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

        showBugReportingDialog(MainPreferences(this))

        installationViewModel.serverStatus.observe(this) {
            if(it == ServerStatus.InstallationError) {
                progressTextView.text = "FATAL ERROR"
                progressTextView.textSize = 32.0f
            } else {
                progressTextView.text = "${it.getInstallationProgress()}%"
            }
            setItemsState(it)
            continueButton.isEnabled = it == ServerStatus.Running
        }

        installationViewModel.installErrorDescription.observe(this) {
            errorContentsTextView.text = it
        }

        continueButton.setOnClickListener {
            stopService(Intent(this, OctoPrintService::class.java))
            val intent = Intent(this, InitialActivity::class.java)
            startActivity(intent)
        }

        logsButton.setOnClickListener {
            val logsFragment = TerminalSheetDialog()
            logsFragment.show(supportFragmentManager, logsFragment.tag)
        }
        seeLogsButton.setOnClickListener {
            val logsFragment = TerminalSheetDialog()
            logsFragment.show(supportFragmentManager, logsFragment.tag)
        }
    }

    private fun setItemsState(status: ServerStatus) {
        if(status == ServerStatus.InstallationError) {
            progressList.visibility = View.GONE
            errorWrapper.visibility = View.VISIBLE
            mainInstallationLayout.background = resources.getDrawable(R.drawable.red_gradient)
        } else {
            progressList.visibility = View.VISIBLE
            errorWrapper.visibility = View.GONE
            mainInstallationLayout.background = resources.getDrawable(R.drawable.green_gradient)
        }
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