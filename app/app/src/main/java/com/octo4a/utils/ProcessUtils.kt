package com.octo4a.utils

import com.bugsnag.android.Bugsnag
import com.octo4a.repository.LogType
import com.octo4a.repository.LoggerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> withIO(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.IO, block)

fun Process.waitAndPrintOutput(logger: LoggerRepository, type: LogType = LogType.BOOTSTRAP): String {
    var outputStr = ""
    inputStream.reader().forEachLine {
        logger.log(this, type) { it }
        outputStr += it
    }


   val exitCode =  waitFor()
    if (exitCode != 0) {
        val uselessWarnings = arrayListOf(
            "proot warning: can't sanitize binding \"/data/data/com.octo4a/files/serialpipe\": No such file or directory",
            "WARNING: linker: ./root/bin/proot: unused DT entry:"
        )
        val logLines = outputStr
            // replace useless proot warning
            .let {
                uselessWarnings.runningFold(it) { curr, valueToReplace ->
                    curr.replace(valueToReplace, "")
                }
            }
            .toString()
            .lines()
            .filter { it.trim() != "" }
            .takeLast(2)
            .joinToString("\n") { it.take(50) }
        throw RuntimeException("Process exited with error code ${exitCode}. $logLines")
    }

    return outputStr
}

fun Process.getOutputAsString(): String {
    val log = StringBuilder()
    var line: String?
    while (inputStream.bufferedReader().readLine().also { line = it } != null) {
        log.append(line + "\n")
    }

    return log.toString()
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