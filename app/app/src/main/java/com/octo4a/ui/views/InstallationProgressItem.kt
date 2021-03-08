package com.octo4a.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import com.octo4a.R
import kotlinx.android.synthetic.main.view_installation_item.view.*

class InstallationProgressItem @JvmOverloads
constructor(private val ctx: Context, private val attributeSet: AttributeSet? = null, private val defStyleAttr: Int = 0)
    : ConstraintLayout(ctx, attributeSet, defStyleAttr) {

    init {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.view_installation_item, this)
    }



    var isLoading: Boolean
        get() = false
        set(value) {
            spinnerView.isGone = !value
            doneIconView.isGone = value
            if (!value) {
                contentTextView.alpha = 0.4F
            }
        }

}