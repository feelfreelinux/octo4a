package com.octo4a.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking

enum class LogType {
    SYSTEM,
    BOOTSTRAP,
    OCTOPRINT,
    OTHER
}

data class LogEntry(val entry: String, val type: LogType = LogType.SYSTEM)

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

        Log.d(tag, getMessage())

        runBlocking {
            _logHistoryFlow.emit(LogEntry(getMessage(), type))
        }
    }
}