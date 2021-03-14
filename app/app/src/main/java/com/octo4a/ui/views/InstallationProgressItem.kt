package com.octo4a.ui.views

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import com.octo4a.R
import com.octo4a.repository.ServerStatus
import com.octo4a.utils.animatedAlpha
import kotlinx.android.synthetic.main.view_installation_item.view.*

class InstallationProgressItem @JvmOverloads
constructor(private val ctx: Context, private val attributeSet: AttributeSet? = null, private val defStyleAttr: Int = 0)
    : ConstraintLayout(ctx, attributeSet, defStyleAttr) {

    init {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.view_installation_item, this)
        layoutTransition = LayoutTransition()
    }

    var status: ServerStatus
        get() = ServerStatus.Stopped
        set(value) {
            contentTextView.text = when (value) {
                ServerStatus.InstallingBootstrap -> resources.getString(R.string.installation_step_bootstrap)
                ServerStatus.InstallingDependencies -> resources.getString(R.string.installation_step_dependencies)
                ServerStatus.BootingUp -> resources.getString(R.string.installation_step_bootup)
                ServerStatus.DownloadingOctoPrint -> resources.getString(R.string.installation_step_downloading_octoprint)
                ServerStatus.Running -> resources.getString(R.string.installation_step_done)
                else -> "Unknown status"
            }
        }

    var isLoading: Boolean
        get() = false
        set(value) {
            spinnerView.isGone = !value
            doneIconView.isGone = value
            if (!value) {
                contentTextView.animatedAlpha = 0.4F
                contentTextView.typeface = Typeface.DEFAULT
            } else {
                contentTextView.animatedAlpha = 1F
                contentTextView.typeface = Typeface.DEFAULT_BOLD
            }
        }

    fun setUpcoming() {
        spinnerView.isGone = true
        doneIconView.isGone = true
        contentTextView.animatedAlpha = 0.4F
        contentTextView.typeface = Typeface.DEFAULT
    }

}