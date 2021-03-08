package com.octo4a.utils

import android.util.Log

inline fun Any.log(message: () -> String) {
    Log.d("${this::class.java.simpleName}: ", message())
}