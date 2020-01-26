package io.feelfreelinux.octo4a.octo4a

import android.annotation.TargetApi
import android.os.Build
import android.util.Log

fun Process.waitAndPrintOutput() {
    inputStream.reader().forEachLine {
        Log.v("ASD", it)
    }
}

fun Process.isRunning(): Boolean {
    try {
        exitValue()
    } catch (_: Throwable) {
        return true
    }
    return false
}