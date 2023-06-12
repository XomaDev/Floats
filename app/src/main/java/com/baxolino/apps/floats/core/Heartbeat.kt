package com.baxolino.apps.floats.core

import android.util.Log
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Heartbeat {

  private const val TAG = "Heartbeat"

  fun beat(exec: TaskExecutor, onStop: () -> Unit) {
    var lastBeat = System.currentTimeMillis()
    exec.register(-1, false) {
      Log.d(TAG, "Beat")
      // we received an heart beat
      lastBeat = System.currentTimeMillis()
    }

    val executor = ScheduledThreadPoolExecutor(1)

    executor.scheduleAtFixedRate({
      // an heart beat
      if ((System.currentTimeMillis() - lastBeat) >= 4000) {
        Log.d(TAG, "Heat beat stopped")

        onStop.invoke()
        exec.unregister(-1)
        executor.shutdownNow()
      }
      Log.d(TAG, "Responding")
      exec.respond(-1)
    }, 0, 2000, TimeUnit.MILLISECONDS)
  }
}