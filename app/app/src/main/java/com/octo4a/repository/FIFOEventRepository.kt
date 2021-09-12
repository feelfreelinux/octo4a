package com.octo4a.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.GsonBuilder
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.lang.Exception

data class FIFOEvent(val eventType: String)

interface FIFOEventRepository {
    val eventState: LiveData<FIFOEvent>
    fun handleFifoEvents()
}

class FIFOEventRepositoryImpl(val logger: LoggerRepository) : FIFOEventRepository {
    private var _eventState = MutableLiveData<FIFOEvent>()
    override val eventState = _eventState

    private val eventFifoPath = "/data/data/com.octo4a/files/bootstrap/bootstrap/eventPipe"
    private val gson by lazy {
        GsonBuilder()
            .create()
    }

    override fun handleFifoEvents() {
        try {
            val fifoFile = File(eventFifoPath)
            while (true) {
                fifoFile.inputStream().bufferedReader().forEachLine {
                    logger.log(this) { "Got event $it" }
                    try {
                        val event = gson.fromJson(it.replace("\n", ""), FIFOEvent::class.java)
                        _eventState.postValue(event)
                    } catch (e: Exception) {
                        logger.log(this) { "Error occured when parsing fifo event " + e.message }
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(this) { "FIFO Handler error " + e.message }
        }
    }

}
