package com.baxolino.apps.floats.core.files

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.baxolino.apps.floats.NsdInterface
import com.baxolino.apps.floats.SessionActivity
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class FileReceiver internal constructor(val name: String, val length: Int) {

  private var service: NsdInterface? = null

  private lateinit var startListener: () -> Unit

  private lateinit var updateListener: () -> Unit
  private lateinit var finishedListener: () -> Unit

  private var cancelled = false

  var startTime = 0L
  var received = 0

  fun setStartListener(listener: () -> Unit) {
    startListener = listener
  }

  fun setUpdateListener(listener: () -> Unit) {
    updateListener = listener
  }

  fun setFinishedListener(listener: () -> Unit) {
    finishedListener = listener
  }

  fun cancel() {
    // a simple cancel message to stop sending
    // more data
    Thread {
      try {
        service!!.output.write(Reasons.REASON_CANCELED)
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }.start()
    val service = Executors.newScheduledThreadPool(1)
    service.schedule({
      // setting this property first will cause
      // interruption when the sender writes data,

      // then it'll check for any messages in it's input stream
      // and there we'll post a code with a reason
      cancelled = true
    }, 60, TimeUnit.MILLISECONDS)
  }

  class EventHandler(private val receiver: FileReceiver) : Handler(Looper.getMainLooper()) {
    override fun handleMessage(message: Message) {
      receiver.apply {
        when (message.what) {
          0 -> {
            // "started" message
            startTime = message.data.getLong("time")
            startListener.invoke()
          }
          1 -> {
            // "update" message
            received = message.arg1
            updateListener.invoke()
          }
          2 -> {
            // "finished" message
            finishedListener.invoke()
          }
        }
      }
    }
  }

  private val handler = EventHandler(this)

  fun receive(session: SessionActivity) {
    val service = Intent(session, FileReceiveService::class.java)
      .putExtra("file_receive", name)
      .putExtra("file_length", length)
      .putExtra("handler", Messenger(handler))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      session.startForegroundService(service)
    else session.startService(service)
  }

  companion object {
    private const val TAG = "FileReceiver"
  }
}