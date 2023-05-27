package com.baxolino.apps.floats.core.files

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.baxolino.apps.floats.SessionActivity


class FileReceiver internal constructor(val name: String, val length: Int) {

  private lateinit var startListener: () -> Unit

  private lateinit var updateListener: () -> Unit
  private lateinit var finishedListener: () -> Unit

  private var cancelled = false

  var startTime = 0L
  var progress = 0

  // formatted transfer speed like 7 Mb(ps)
  var transferSpeed = ""

  fun setStartListener(listener: () -> Unit) {
    startListener = listener
  }

  fun setUpdateListener(listener: () -> Unit) {
    updateListener = listener
  }

  fun setFinishedListener(listener: () -> Unit) {
    finishedListener = listener
  }

  fun cancel(context: Context) {
    cancelled = true

    context.sendBroadcast(Intent()
      .apply {
        action = FileReceiveService.CANCEL_REQUEST_ACTION
      })
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
            progress = message.arg1
            transferSpeed = message.data.getString("speed", "")

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