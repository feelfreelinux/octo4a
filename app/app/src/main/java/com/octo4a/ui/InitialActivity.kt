package com.octo4a.ui

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.octo4a.Octo4aApplication
import com.octo4a.R
import com.octo4a.camera.CameraEnumerationRepository
import com.octo4a.camera.CameraService
import com.octo4a.camera.LegacyCameraService
import com.octo4a.repository.BootstrapRepository
import com.octo4a.repository.GithubRelease
import com.octo4a.service.OctoPrintService
import com.octo4a.utils.getOutputAsString
import com.octo4a.utils.isServiceRunning
import com.octo4a.utils.preferences.MainPreferences
import com.octo4a.utils.waitAndPrintOutput
import com.octo4a.viewmodel.BootstrapItem
import com.octo4a.viewmodel.InstallationViewModel
import kotlinx.android.synthetic.main.activity_landing.*
import kotlinx.android.synthetic.main.bootstrap_spinner_view.view.bootstrapVersion
import kotlinx.android.synthetic.main.bootstrap_spinner_view.view.octoprintVersion
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class BootstrapSpinnerAdapter(context: Context, @LayoutRes private val layoutResource: Int, private val releases: List<BootstrapItem>):
    ArrayAdapter<BootstrapItem>(context, layoutResource, releases) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return createViewFromResource(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return createViewFromResource(position, convertView, parent)
    }

    private fun createViewFromResource(position: Int, convertView: View?, parent: ViewGroup?): View{
        val view = convertView ?: LayoutInflater.from(context).inflate(layoutResource, parent, false)
        val release = releases[position]
        view.octoprintVersion.text = release.title

        var bootstrapVersionText = release.bootstrapVersion

        if (release.recommended) {
            bootstrapVersionText += " • recommended"
        } else if (release.prerelease) {
            bootstrapVersionText += " • prerelease"
        }
        view.bootstrapVersion.text = bootstrapVersionText
        return view
    }
}
class InitialActivity: AppCompatActivity() {
    private val bootstrapRepository: BootstrapRepository by inject()
    private val prefs: MainPreferences by inject()
    private val cameraEnumerationRepository: CameraEnumerationRepository by inject()
    private val installationViewModel: InstallationViewModel by viewModel()
    private val spinnerArrayAdapter: BootstrapSpinnerAdapter by lazy { BootstrapSpinnerAdapter(this, R.layout.bootstrap_spinner_view, mutableListOf()) }

    // Storage permission request
    private val hasStoragePermission: Boolean
        get() = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    // Detects legacy (<1.0.0) release bootstrap
    private val pre1LegacyBootstrapInstalled by lazy { File("/data/data/com.octo4a/files/home").exists() }
    private val legacyBootstrapInstalled by lazy { File("/data/data/com.octo4a/files/bootstrap/add-user.sh").exists() }

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

        installationViewModel.bootstrapReleases.observe(this) {
            updateBootstrapSpinnerItems(it)
        }

        // Setup spinner's adapter
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bootstrapVersionSpinner.adapter = spinnerArrayAdapter
        bootstrapVersionSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                installationViewModel.selectBootstrapRelease(spinnerArrayAdapter.getItem(position)!!)
            }
        }

        if (legacyBootstrapInstalled) {
            showLegacyBootstrapDialog()
        } else if (pre1LegacyBootstrapInstalled) {
            showPre1LegacyBootstrapDialog()
        } else {
            prepareBootstrap()
        }
    }

    private fun updateBootstrapSpinnerItems(releases: List<BootstrapItem>) {
        spinnerArrayAdapter.clear()
        spinnerArrayAdapter.addAll(releases)
        spinnerArrayAdapter.notifyDataSetChanged()

        val recommendedItem = releases.indexOfFirst { it.recommended }

        if (recommendedItem > -1) {
            bootstrapVersionSpinner.setSelection(recommendedItem)
        }
    }

    private fun prepareBootstrap() {
        if (bootstrapRepository.isBootstrapInstalled) {
            checkWritePermissionAndRun()
        }

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

        installationViewModel.fetchBootstrapReleases()
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
            if (!bootstrapRepository.isBootstrapInstallItemSelected) {
                Toast.makeText(this, getString(R.string.bootstrap_not_selected), Toast.LENGTH_LONG).show()
            } else {
                startOctoService()
                val intent = Intent(this, InstallationActivity::class.java)
                startActivity(intent)
                finish()
            }
        } else {
            startOctoService()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo != null && cm.activeNetworkInfo.isConnected
    }

    private fun startOctoService() {
        if (!isServiceRunning(OctoPrintService::class.java)) {
            val intent = Intent(this, OctoPrintService::class.java)
            startService(intent)
        }
    }
    private fun showLegacyBootstrapDialog() {
        val alertDialog: AlertDialog? = this.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setTitle(R.string.legacy_bootstrap)
                setMessage(R.string.legacy_bootstrap_msg)
                setNeutralButton(android.R.string.cancel) { dialog, id ->
                }

                setPositiveButton(R.string.clear_and_install) { dialog, id ->
                    // Remove all bootstrap-related data
                    bootstrapRepository.runCommand("cd .. && rm -rf *", prooted = false).getOutputAsString()
                    prepareBootstrap()
                }

            }

            builder.create()
        }

        alertDialog?.show()
    }

    private fun showPre1LegacyBootstrapDialog() {
        val alertDialog: AlertDialog? = this.let {
            val builder = AlertDialog.Builder(it)
            builder.apply {
                setTitle(R.string.legacy_bootstrap)
                setMessage(R.string.legacy_bootstrap_msg)
                setNeutralButton(android.R.string.cancel) { dialog, id ->
                }

                setPositiveButton(R.string.clear_and_install) { dialog, id ->
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    // clearing app data
                    if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
                        activityManager.clearApplicationUserData()
                    } else {
                        val packageName = applicationContext.packageName
                        val runtime = Runtime.getRuntime();
                        runtime.exec("pm clear $packageName");
                    }

                    prepareBootstrap()
                }

            }

            builder.create()
        }

        alertDialog?.show()
    }
}