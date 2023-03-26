package com.octo4a.ui.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.ColorFilter
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.octo4a.R
import com.octo4a.viewmodel.IPAddress
import com.octo4a.viewmodel.IPAddressType
import kotlinx.android.synthetic.main.view_ip_address.view.*

class IPAddressView@JvmOverloads
constructor(private val ctx: Context, private val attributeSet: AttributeSet? = null, private val defStyleAttr: Int = 0)
    : ConstraintLayout(ctx, attributeSet, defStyleAttr)  {
    init {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.view_ip_address, this)
        ipAddressView.setOnClickListener {
            // copy ipAddressTextView.text to clipboard
            val clipboard =
                ctx.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip = ClipData.newPlainText("octo4a", ipAddressTextView.text)

            // Only show a toast for Android 12 and lower.
            if (Build.VERSION.SDK_INT <= 32) { // API 32 is Android 12
                Toast.makeText(
                    context,
                    ctx.getString(R.string.ip_address_copied_to_clipboard),
                    Toast.LENGTH_SHORT
                ).show()

            }
        }
    }

    var ipAddress: IPAddress
        get() = IPAddress(IPAddressType.Wifi, "")
        set(value) {
            connectionTypeIcon.setImageResource(
                when (value.type) {
                    IPAddressType.Wifi -> R.drawable.wifi
                    IPAddressType.Cellular -> R.drawable.cellular
                    IPAddressType.Ethernet -> R.drawable.ethernet
                    IPAddressType.VPN -> R.drawable.vpn

                }
            )
            connectionTypeIcon.imageAlpha = when (value.type) {
                IPAddressType.Cellular -> 100
                else -> 255
            }
            ipAddressTextView.setTypeface(null, when (value.type) {
                IPAddressType.Cellular -> android.graphics.Typeface.NORMAL
                else -> android.graphics.Typeface.BOLD
            })
            ipAddressTextView.text = value.address + if (value.port != "") ":${value.port}" else ""
        }
}