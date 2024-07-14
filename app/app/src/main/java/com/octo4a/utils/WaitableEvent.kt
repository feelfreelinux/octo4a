package com.octo4a.utils

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class WaitableEvent {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var isSet = false

    fun wait(autoreset: Boolean = false) {
        lock.withLock {
            while (!isSet) {
                condition.await()
            }
            if( autoreset ) {
              reset()
            }
        }
    }

    fun set() {
        lock.withLock {
            isSet = true
            condition.signalAll()
        }
    }

    fun reset() {
        lock.withLock {
            isSet = false
        }
    }
}