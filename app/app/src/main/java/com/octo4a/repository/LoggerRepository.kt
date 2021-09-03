package com.octo4a.repository

import android.graphics.Color
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking

enum class LogType {
    SYSTEM,
    BOOTSTRAP,
    OCTOPRINT,
    EXTENSION,
    OTHER
}

data class LogEntry(val entry: String, val type: LogType = LogType.SYSTEM)

fun LogType.getTypeEmoji(): String {
    return when (this) {
        LogType.BOOTSTRAP -> {
            "\uD83D\uDC38"
        }
        LogType.OCTOPRINT -> {
            "\uD83D\uDC19"
        }
        LogType.EXTENSION -> {
            "\uD83D\uDD0C"
        }
        LogType.SYSTEM -> {
            "\uD83D\uDCBB"
        }
        else ->"❓"
    }
}

interface LoggerRepository {
    val logHistoryFlow: SharedFlow<LogEntry>
    fun log(obj: Any? = null, type: LogType = LogType.SYSTEM, getMessage: () -> String)
}

class LoggerRepositoryImpl: LoggerRepository {
    private var _logHistoryFlow = MutableSharedFlow<LogEntry>(500)

    override val logHistoryFlow: SharedFlow<LogEntry>
        get() = _logHistoryFlow

    override fun log(obj: Any?, type: LogType, getMessage: () -> String) {
        var tag = "octo4a: "
        obj?.let {
            tag = obj::class.java.simpleName + ": "
        }

        Log.d(type.getTypeEmoji(), getMessage())

        runBlocking {
            Looper.getMainLooper().run {
                _logHistoryFlow.emit(LogEntry(getMessage(), type))
            }
        }
    }
}