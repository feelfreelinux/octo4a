package com.octo4a.ui.fragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.camera.core.Preview
import androidx.core.view.isGone
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnSliderTouchListener
import com.octo4a.R
import com.octo4a.camera.CameraService
import com.octo4a.repository.LoggerRepository
import com.octo4a.utils.isServiceRunning
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.android.synthetic.main.dialog_camera_preview.manualFocusCheckbox
import kotlinx.android.synthetic.main.dialog_camera_preview.manualFocusSlider
import kotlinx.android.synthetic.main.dialog_camera_preview.manualFocusText
import kotlinx.android.synthetic.main.dialog_camera_preview.view.previewView
import org.koin.android.ext.android.inject

@RequiresApi(Build.VERSION_CODES.LOLLIPOP) // not used on legacy devices
class CameraPreviewDialogFragment : DialogFragment() {
    private var createdView: View? = null

    private var _cameraService: CameraService? = null
    private val mainPreferences: MainPreferences by inject()
    private val logger: LoggerRepository by inject()
    private var _cameraPreview: Preview? = null

    private val cameraServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as CameraService.LocalBinder
            _cameraService = binder.getService()
            assert(createdView != null)
            initializeWithService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            _cameraService = null
        }
    }

    override fun getView(): View? {
        return createdView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        createdView = onCreateView(LayoutInflater.from(requireContext()), null, savedInstanceState)
        builder.setView(createdView)
        builder.setTitle(R.string.camera_preview)
        return builder.create()
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
        _cameraService = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_camera_preview, container, false)
    }

    private fun reinitPreview() {
       _cameraService?.updateCameraParameters()
    }

    private fun initializeWithService() {
        _cameraPreview = _cameraService!!.getPreview()
        _cameraService?.hookPreviewLifecycleObserver(this)
        logger.log { "init with service" }
        val minFocalLength = _cameraService?.getCameraMinFocalLength()
        val supportsManualFocus = minFocalLength != null && minFocalLength > 0f

        view?.apply {
            _cameraPreview?.setSurfaceProvider(previewView.surfaceProvider)
            manualFocusSlider?.isGone = !(mainPreferences.manualAF && supportsManualFocus)
            manualFocusCheckbox?.isGone = !supportsManualFocus
            manualFocusCheckbox?.isChecked = supportsManualFocus && mainPreferences.manualAF
            manualFocusCheckbox.setOnCheckedChangeListener { _, checked ->
                manualFocusSlider.isGone = !checked
                mainPreferences.manualAF = checked
                reinitPreview()
            }

            if (supportsManualFocus) {
                manualFocusSlider.valueTo = _cameraService!!.getCameraMinFocalLength() ?: 0f
                manualFocusSlider.addOnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        mainPreferences.manualAFValue = value
                    }
                }

                manualFocusSlider?.addOnSliderTouchListener(object : OnSliderTouchListener {
                    @SuppressLint("RestrictedApi")
                    override fun onStartTrackingTouch(p0: Slider) {
                    }

                    @SuppressLint("RestrictedApi")
                    override fun onStopTrackingTouch(p0: Slider) {
                        // reset the preview
                        reinitPreview()
                    }
                })

                manualFocusSlider.value = mainPreferences.manualAFValue

                // make the text also clickable
                manualFocusText.setOnClickListener {
                    manualFocusCheckbox.performClick()
                }
            }
        }
    }
}