package com.octo4a.utils

import com.bugsnag.android.Bugsnag
import com.octo4a.repository.LogType
import com.octo4a.repository.LoggerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.Date

suspend fun <T> withIO(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.IO, block)

fun Process.waitAndPrintOutput(
    logger: LoggerRepository,
    type: LogType = LogType.BOOTSTRAP
): String {
    var outputStr = ""
    inputStream.reader().forEachLine {
        logger.log(this, type) { it }
        outputStr += it
    }


    val exitCode = waitFor()
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

/**
 * retryOperation will retry the operation until it succeeds or the maxRetries is reached
 * It does not retry if the operation takes less than minSeconds, to avoid retrying operations that fail instantly.
 * @param logger the logger to use
 * @param maxRetries the maximum number of retries
 * @param minSeconds the minimum time the operation needs to run to be retried
 */
fun retryOperation(
    logger: LoggerRepository,
    maxRetries: Int = 2,
    minSeconds: Int = 6,
    op: () -> Unit
) {
    var timesLeft = maxRetries

    while (true) {
        var started = Date()
        try {

            op()
            return
        } catch (e: java.lang.Exception) {
            timesLeft--
            var now = Date()
            // Don't wanna use Duration.between because it's not available on API 24
            if (now.time - started.time < minSeconds * 1000) {
                throw e
            }
            if (timesLeft <= 0) {
                throw e
            }
            logger.log { "An error has occurred:$e" }
            logger.log { "Retries left: $timesLeft/$maxRetries" }
        }
    }
}