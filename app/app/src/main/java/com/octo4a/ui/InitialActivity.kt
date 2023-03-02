package com.octo4a.ui

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.octo4a.R
import com.octo4a.camera.CameraEnumerationRepository
import com.octo4a.repository.BootstrapRepository
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.android.synthetic.main.activity_landing.*
import org.koin.android.ext.android.inject
import java.io.File

class InitialActivity: AppCompatActivity() {
    private val bootstrapRepository: BootstrapRepository by inject()
    private val prefs: MainPreferences by inject()
    private val cameraEnumerationRepository: CameraEnumerationRepository by inject()

    // Storage permission request
    private val hasStoragePermission: Boolean
        get() = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    // Detects legacy (<1.0.0) release bootstrap
    private val legacyBootstrapInstalled by lazy { File("/data/data/com.octo4a/files/home").exists() }

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted ->
            if (isGranted.values.all { it }) {
                startApp()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.currentRelease.isNullOrBlank()) {
            prefs.currentRelease = "1.0.1"
        }

        supportActionBar?.hide()
        setContentView(R.layout.activity_landing)

        if (legacyBootstrapInstalled) {
            showLegacyBootstrapDialog()
        }

        if (bootstrapRepository.isBootstrapInstalled) {
            checkWritePermissionAndRun()
        }

        prepareBootstrap()
    }

    private fun prepareBootstrap() {
        cameraEnumerationRepository.enumerateCameras()
        installButton.setOnClickListener {
            if (legacyBootstrapInstalled) {
                showLegacyBootstrapDialog()
            } else if (!isNetworkConnected()) {
                Toast.makeText(this, getString(R.string.missing_network), Toast.LENGTH_LONG).show()
            } else {
                checkWritePermissionAndRun()
            }
        }
    }

    private fun checkWritePermissionAndRun() {
        if (!hasStoragePermission) {
            requestStoragePermission.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
            Toast.makeText(this, getString(R.string.missing_write_permission), Toast.LENGTH_LONG).show()
        }
        else {
            startApp()
        }
    }

    private fun startApp() {
        if (!bootstrapRepository.isBootstrapInstalled) {
            val intent = Intent(this, InstallationActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo != null && cm.activeNetworkInfo.isConnected
    }

    private fun showLegacyBootstrapDialog() {
        val alertDialog: AlertDialog? = this.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setTitle(R.string.legacy_bootstrap)
                setMessage(R.string.legacy_bootstrap_msg)
                setNeutralButton(android.R.string.cancel) { _, _ ->
                }

                setPositiveButton(R.string.clear_and_install) { _, _ ->
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    // clearing app data
                    if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
                        activityManager.clearApplicationUserData()
                    } else {
                        val packageName = applicationContext.packageName
                        val runtime = Runtime.getRuntime()
                        runtime.exec("pm clear $packageName")
                    }

                    prepareBootstrap()
                }

            }

            builder.create()
        }

        alertDialog?.show()
    }
}