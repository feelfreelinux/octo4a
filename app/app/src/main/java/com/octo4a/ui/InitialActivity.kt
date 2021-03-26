package com.octo4a.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleService
import com.octo4a.R
import com.octo4a.service.CameraService
import com.octo4a.service.OctoPrintService
import com.octo4a.repository.BootstrapRepository
import com.octo4a.utils.isServiceRunning
import com.octo4a.utils.log
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.android.synthetic.main.activity_landing.*
import org.koin.android.ext.android.inject

class InitialActivity: AppCompatActivity() {
    private val bootstrapRepository: BootstrapRepository by inject()
    private val prefs: MainPreferences by inject()
    private val pm  by lazy { getSystemService(LifecycleService.POWER_SERVICE) as PowerManager }


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
            // Required for acquiring wakelock
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val whitelist = Intent()
                whitelist.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                whitelist.data = Uri.parse("package:$packageName")
                whitelist.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    startActivity(whitelist)
                } catch (e: ActivityNotFoundException) {
                    log { "failed to open battery optimization dialog" }
                }
            } else {
                startOctoService()
                val intent = Intent(this, InstallationActivity::class.java)
                startActivity(intent)
                finish()
            }
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