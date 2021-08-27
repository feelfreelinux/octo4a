package com.octo4a.ui.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.octo4a.R
import com.octo4a.camera.CameraService
import com.octo4a.repository.GithubRelease
import com.octo4a.repository.LoggerRepository
import com.octo4a.repository.ServerStatus
import com.octo4a.serial.VirtualSerialDriver
import com.octo4a.ui.views.UsbDeviceView
import com.octo4a.utils.preferences.MainPreferences
import com.octo4a.viewmodel.StatusViewModel
import kotlinx.android.synthetic.main.fragment_server.*
import kotlinx.android.synthetic.main.view_status_card.view.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel


class ServerFragment : Fragment() {
    private val statusViewModel: StatusViewModel by sharedViewModel()
    private lateinit var cameraService: CameraService
    private var boundToCameraService = false
    private val vspDriver: VirtualSerialDriver by inject()
    private val mainPreferences: MainPreferences by inject()
    private val logger: LoggerRepository by inject()

    private val cameraServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as CameraService.LocalBinder
            cameraService = binder.getService()
            boundToCameraService = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            boundToCameraService = false
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_server, container, false)
    }

    override fun onStart() {
        super.onStart()
        val activity = requireActivity()
        Intent(activity, CameraService::class.java).also { intent ->
            activity.bindService(intent, cameraServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        requireActivity().unbindService(cameraServiceConnection)
        boundToCameraService = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusViewModel.updateAvailable.observe(viewLifecycleOwner) {
            logger.log(this) { "Update available" }
            showUpdateDialog(it)
        }

        vspDriver.connectedDevices.asLiveData().observe(viewLifecycleOwner) { devices ->
            usbDevicesList.removeAllViews()
            devices.forEach {
                val usbDeviceView = UsbDeviceView(requireContext(), vspDriver)
                usbDevicesList.addView(usbDeviceView)
                usbDeviceView.setUsbDevice(it)
            }
        }

        statusViewModel.usbStatus.observe(viewLifecycleOwner) {
            if (it.isAttached) {
                connectionStatus.title = getString(R.string.connection_connected)
                connectionStatus.subtitle = it.port
            } else {
                connectionStatus.title = getString(R.string.connection_not_connected)
                connectionStatus.subtitle = getString(R.string.connection_otg_cable)
            }
        }

        camServerStatus.setOnClickListener {
            if (statusViewModel.cameraStatus.value == true) {
                showPreviewDialog()
            }
        }

        statusViewModel.cameraStatus.observe(viewLifecycleOwner) {
            if (it) {
                camServerStatus.title = getString(R.string.camserver_running)
                camServerStatus.subtitle = getString(R.string.camserver_status_tap)
            } else {
                camServerStatus.title = getString(R.string.camserver_disabled)
                camServerStatus.subtitle = getString(R.string.camserver_enable)
            }
        }

        statusViewModel.serverStatus.observe(viewLifecycleOwner) {
            when (it) {
                ServerStatus.Running -> {
                    serverStatus.setDrawableAndColor(R.drawable.ic_stop_24px, android.R.color.holo_red_light)
                    serverStatus.title = resources.getString(R.string.status_running)
                    serverStatus.onActionClicked = {
                        statusViewModel.stopServer()
                    }
                    serverStatus.subtitle = statusViewModel.getServerAddress()
                }

                ServerStatus.BootingUp -> {
                    serverStatus.title = resources.getString(R.string.status_starting)
                    serverStatus.subtitle = resources.getString(R.string.status_starting_subtitle)
                }

                ServerStatus.ShuttingDown -> {
                    serverStatus.title = resources.getString(R.string.status_shutting_down)
                    serverStatus.subtitle = resources.getString(R.string.status_shutting_down_subtitle)
                }

                ServerStatus.Stopped -> {
                    serverStatus.setDrawableAndColor(R.drawable.ic_play_arrow_24px, R.color.iconGreen)
                    serverStatus.title = resources.getString(R.string.status_stopped)
                    serverStatus.subtitle = resources.getString(R.string.status_stopped_start)
                    serverStatus.onActionClicked = {
                        statusViewModel.startServer()
                    }
                }
                else -> {}
            }
            serverStatus.actionProgressbar.isGone = !it.progress
            serverStatus.actionButton.isGone = it.progress
        }

        // Fetch autoupdater
        statusViewModel.checkUpdateAvailable()
    }

    private fun showUpdateDialog(update: GithubRelease) {
        if (update.tagName != mainPreferences.updateDismissed) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.update_available))
                .setMessage(getString(R.string.update_available_message).format(update.tagName))
                .setPositiveButton(getString(R.string.action_download)) { dialog, _ ->
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(update.htmlUrl)
                    startActivity(i)
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.action_later)) { dialog, _ ->
                    dialog.dismiss()
                    mainPreferences.updateDismissed = update.tagName
                }
                .show()
        }
    }

    private fun showPreviewDialog() {
//        val dialog = MaterialAlertDialogBuilder(requireActivity())
//            .setTitle(R.string.camera_preview)
//            .setView(R.layout.dialog_camera_preview)
//            .setPositiveButton(R.string.action_ok) {dialog, _ -> dialog.dismiss() }
//            .show()
//        val surfaceProvider = dialog.findViewById<PreviewView>(R.id.previewView)?.surfaceProvider
//        if (boundToCameraService) {
//            cameraService.getPreview().setSurfaceProvider(surfaceProvider)
//        }
    }
}