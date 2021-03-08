package com.octo4a.utils

import android.view.View
import android.view.animation.LinearInterpolator

var View.animatedAlpha: Float
    get() = alpha
    set(value) {
        animate().apply {
            interpolator = LinearInterpolator()
            duration = 300
            alpha(value)
            start()
        }
    }