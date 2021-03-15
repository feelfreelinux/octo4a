package com.octo4a.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> withIO(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.IO, block)

fun Process.waitAndPrintOutput() {
    inputStream.reader().forEachLine {
        log { it }
    }
}

fun Process.setPassword(password: String) {
    outputStream.bufferedWriter().apply {
        write("$password\n")
        flush()
        write("$password\n")
        flush()
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