package com.octo4a.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater

import androidx.constraintlayout.widget.ConstraintLayout
import com.octo4a.R
import com.octo4a.repository.ExtensionStatus
import com.octo4a.repository.ExtensionsRepository
import com.octo4a.repository.RegisteredExtension
import kotlinx.android.synthetic.main.extension_view.view.*

class ExtensionView @JvmOverloads
constructor(private val ctx: Context, private val extensionsRepository: ExtensionsRepository, private val attributeSet: AttributeSet? = null, private val defStyleAttr: Int = 0)
    : ConstraintLayout(ctx, attributeSet, defStyleAttr) {

    var onActionClicked: () -> Unit = {}

    init {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.extension_view, this)
    }

    fun setExtensionDescription(extensionInfo: RegisteredExtension) {
        titleText.text = extensionInfo.title

        val status = when (extensionInfo.status) {
            ExtensionStatus.Stopped -> " (stopped)"
            ExtensionStatus.Running -> " (running)"
            else -> " (crashed)"
        }

        subtitleText.text = extensionInfo.description + status


        extensionEnabled.isChecked = extensionsRepository.getExtensionSettings(extensionInfo.name)?.enabled ?: false
        extensionEnabled.setOnCheckedChangeListener { _, checked ->
            extensionsRepository.modifyExtensionSetting(extensionInfo.name, checked)
        }
    }
}