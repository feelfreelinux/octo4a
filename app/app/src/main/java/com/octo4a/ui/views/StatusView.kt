package com.octo4a.ui.views

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.octo4a.R
import com.octo4a.repository.ServerStatus
import kotlinx.android.synthetic.main.view_installation_item.view.*
import kotlinx.android.synthetic.main.view_status_card.view.*


class StatusView @JvmOverloads
constructor(private val ctx: Context, private val attributeSet: AttributeSet? = null, private val defStyleAttr: Int = 0)
    : ConstraintLayout(ctx, attributeSet, defStyleAttr) {

    var onActionClicked: () -> Unit = {}

    init {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.view_status_card, this)
        setPadding(12)
        attributeSet?.apply {
            val styledAttributes = context.obtainStyledAttributes(this, R.styleable.StatusView, 0, 0)
            val titleValue = styledAttributes.getString(R.styleable.StatusView_title)
            val subtitleValue = styledAttributes.getString(R.styleable.StatusView_subtitle)
            val icon = styledAttributes.getDrawable(R.styleable.StatusView_icon)
            val iconColor = styledAttributes.getColor(R.styleable.StatusView_iconColor, 0)

            val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.circle_drawable)
            backgroundDrawable!!.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
            iconCircle.background = backgroundDrawable

            iconCircle.iconView.setImageDrawable(icon)
            titleText.text = titleValue
            subtitleText.text = subtitleValue
            styledAttributes.recycle()
        }

        actionButton.setOnClickListener {
            onActionClicked()
        }
    }

    fun setDrawableAndColor(@DrawableRes resource: Int, @ColorRes colorRes: Int) {
        val backgroundDrawable = ContextCompat.getDrawable(context, resource)
        val color = ContextCompat.getColor(context, colorRes);
        backgroundDrawable!!.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        actionButton.background = backgroundDrawable
    }

    var title: String
        get() = ""
        set(value) {
            titleText.text = value
        }

    var subtitle: String
        get() = ""
        set(value) {
            subtitleText.text = value
        }
    }