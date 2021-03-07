package com.octo4a.utils

import android.util.Log

fun Process.waitAndPrintOutput() {
    inputStream.reader().forEachLine {
        Log.v("ASD", it)
    }
}