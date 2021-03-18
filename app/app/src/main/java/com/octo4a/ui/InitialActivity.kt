package com.octo4a.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.octo4a.R
import com.octo4a.service.CameraService
import com.octo4a.service.OctoPrintService
import com.octo4a.repository.BootstrapRepository
import com.octo4a.utils.isServiceRunning
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.android.synthetic.main.activity_landing.*
import org.koin.android.ext.android.inject

class InitialActivity: AppCompatActivity() {
    private val bootstrapRepository: BootstrapRepository by inject()
    private val prefs: MainPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.activity_landing)

        if (bootstrapRepository.isBootstrapInstalled) {
            startOctoService()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        installButton.setOnClickListener {
            startOctoService()
            val intent = Intent(this, InstallationActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun startOctoService() {
        if (!isServiceRunning(OctoPrintService::class.java)) {
            val intent = Intent(this, OctoPrintService::class.java)
            startService(intent)
        }

        if (!isServiceRunning(CameraService::class.java) && prefs.enableCameraServer) {
            val intent = Intent(this, CameraService::class.java)
            startService(intent)
        }
    }
}