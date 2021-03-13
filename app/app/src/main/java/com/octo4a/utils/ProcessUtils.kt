package com.octo4a.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> withIO(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.IO, block)

fun Process.waitAndPrintOutput() {
    inputStream.reader().forEachLine {
        Log.v("ASD", it)
    }
}

fun Process.setPassword() {
    outputStream.bufferedWriter().apply {
        write("octoprint\n")
        flush()
        write("octoprint\n")
        flush()
    }
}