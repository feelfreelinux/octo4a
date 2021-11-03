package com.octo4a.ui.views

import android.animation.LayoutTransition
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.octo4a.R
import com.octo4a.serial.ConnectedUsbDevice
import com.octo4a.serial.SerialDriverClass
import com.octo4a.serial.VirtualSerialDriver
import com.octo4a.serial.getCustomPrinterProber
import kotlinx.android.synthetic.main.select_driver_bottom_sheet.*
import kotlinx.android.synthetic.main.view_usb_devices_item.view.*

class UsbDeviceView @JvmOverloads
constructor(private val ctx: Context, private val vsp: VirtualSerialDriver, private val attributeSet: AttributeSet? = null, private val defStyleAttr: Int = 0)
    : ConstraintLayout(ctx, attributeSet, defStyleAttr) {

    private val prober by lazy { getCustomPrinterProber() }

    init {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.view_usb_devices_item, this)
        layoutTransition = LayoutTransition()
    }

    fun drawDriverInfo(usbDevice: ConnectedUsbDevice) {
        val driverText = when (usbDevice.driverClass) {
            SerialDriverClass.CDC -> "CDC"
            SerialDriverClass.CH341 -> "CH341"
            SerialDriverClass.PROLIFIC -> "Prolific"
            SerialDriverClass.FTDI -> "FTDI"
            SerialDriverClass.CP21XX -> "CP21xx"
            SerialDriverClass.UNKNOWN -> "Unknown"
        }

        serialDriverText.text = driverText + context.getString(R.string.serial_driver)
        if (!usbDevice.autoDetect) {
            serialDriverText.text = serialDriverText.text.toString() + context.getString(R.string.tap_to_select)
        }
    }

    fun setUsbDevice(usbDevice: ConnectedUsbDevice) {
        vidPidText.text = "VID " + usbDevice.vendorId.toString(16) + " / PID " + usbDevice.productId.toString(16)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            titleText.text = usbDevice.device.productName
        }

        drawDriverInfo(usbDevice)
        selectCheckbox.isChecked = usbDevice.isSelected
        selectCheckbox.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (usbDevice.driverClass != SerialDriverClass.UNKNOWN) {
                    vsp.tryToSelectDevice(usbDevice)
                } else {
                    selectCheckbox.isChecked = false
                    Toast.makeText(context, context.getString(R.string.requesting_usb_permission), Toast.LENGTH_LONG).show()
                }
            } else {
                selectCheckbox.isChecked = true
            }
        }


        setOnClickListener {
            openBottomSheet(usbDevice)
        }
    }

    fun openBottomSheet(usbDevice: ConnectedUsbDevice) {
        if (usbDevice.autoDetect) {
            return
        }
        val bottomSheetDialog = BottomSheetDialog(context)
        bottomSheetDialog.setContentView(R.layout.select_driver_bottom_sheet)


        bottomSheetDialog.apply {
            ftdi.setOnClickListener {
                usbDevice.driverClass = SerialDriverClass.FTDI
                drawDriverInfo(usbDevice)
                vsp.tryToSelectDevice(usbDevice)
                dismiss()
            }

            cp21xx.setOnClickListener {
                usbDevice.driverClass = SerialDriverClass.CP21XX
                drawDriverInfo(usbDevice)
                vsp.tryToSelectDevice(usbDevice)
                dismiss()
            }

            prolific.setOnClickListener {
                usbDevice.driverClass = SerialDriverClass.PROLIFIC
                drawDriverInfo(usbDevice)
                vsp.tryToSelectDevice(usbDevice)
                dismiss()
            }

            cdcAcm.setOnClickListener {
                usbDevice.driverClass = SerialDriverClass.CDC
                drawDriverInfo(usbDevice)
                vsp.tryToSelectDevice(usbDevice)
                dismiss()
            }

            ch341.setOnClickListener {
                usbDevice.driverClass = SerialDriverClass.CH341
                drawDriverInfo(usbDevice)
                vsp.tryToSelectDevice(usbDevice)
                dismiss()
            }
        }
        bottomSheetDialog.show()
    }
}