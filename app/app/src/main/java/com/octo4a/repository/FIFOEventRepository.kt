package com.octo4a.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.octo4a.utils.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.lang.Exception

data class FIFOEvent(val eventType: String)

interface FIFOEventRepository {
    val eventState: LiveData<FIFOEvent>
    fun handleFifoEvents()
}

class FIFOEventRepositoryImpl : FIFOEventRepository {
    private var _eventState = MutableLiveData<FIFOEvent>()
    override val eventState = _eventState

    private val eventFifoPath = "/data/data/com.octo4a/files/home/eventPipe"
    private val gson by lazy {
        GsonBuilder()
            .create()
    }

    override fun handleFifoEvents() {
        try {
            val fifoFile = File(eventFifoPath)
            while (true) {
                fifoFile.inputStream().bufferedReader().forEachLine {
                    log { "Got event $it" }
                    try {
                        val event = gson.fromJson(it.replace("\n", ""), FIFOEvent::class.java)
                        _eventState.postValue(event)
                    } catch (e: Exception) {
                        log { "Error occured when parsing fifo event " + e.message }
                    }
                }
            }
        } catch (e: Exception) {
            log { "FIFO Handler error " + e.message }
        }
    }

}
