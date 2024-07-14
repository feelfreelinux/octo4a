package com.octo4a.utils

import android.os.Handler
import android.os.Looper
import java.util.Timer
import java.util.TimerTask

class CancelableTimer(private val handler: Handler = Handler(Looper.getMainLooper())) {
  private var timer: Timer? = null
  private var task: TimerTask? = null

  fun start(delay: Long, callback: () -> Unit) {
    timer?.cancel() // Cancel any existing timer
    timer = Timer()
    task =
        object : TimerTask() {
          override fun run() {
            handler.post { callback() }
          }
        }
    timer?.schedule(task, delay)
  }

  fun cancel() {
    task?.cancel()
    timer?.cancel()
    timer = null
    task = null
  }
}
