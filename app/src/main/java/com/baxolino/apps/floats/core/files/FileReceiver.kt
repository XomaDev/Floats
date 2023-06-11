package com.baxolino.apps.floats.core.files

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.baxolino.apps.floats.SessionActivity
import com.baxolino.apps.floats.core.TaskExecutor


class FileReceiver internal constructor(private val port: Int, val name: String, val length: Int) {

  companion object {
    private const val TAG = "FileReceiver"
  }

  private lateinit var startListener: () -> Unit

  private lateinit var updateListener: () -> Unit
  private lateinit var finishedListener: () -> Unit

  private lateinit var disruptionListener: () -> Unit

  private var cancelled = false

  var startTime = 0L
  var progress = 0

  // formatted transfer speed like 7 Mb(ps)
  var transferSpeed = ""

  init {
    Log.d(TAG, "Receiving On Port $port")
    MessageReceiver.listener = { what, arg1, data ->
      onMessageReceived(what, arg1, data)
    }
  }

  fun setStartListener(listener: () -> Unit) {
    startListener = listener
  }

  fun setUpdateListener(listener: () -> Unit) {
    updateListener = listener
  }

  fun setFinishedListener(listener: () -> Unit) {
    finishedListener = listener
  }

  fun setDisruptionListener(listener: () -> Unit) {
    disruptionListener = listener
  }

  fun cancel(context: Context, exec: TaskExecutor) {
    cancelled = true

    exec.writeCanel(port)
    context.sendBroadcast(Intent(FileReceiveService.CANCEL_RECEIVE_ACTION))
  }

  private fun onMessageReceived(what: Int, arg1: Int, data: Bundle) {
    when (what) {
      0 -> {
        // "started" message
        startTime = data.getLong("time")
        startListener.invoke()
      }
      1 -> {
        // "update" message
        progress = arg1
        transferSpeed = data.getString("speed", "")

        updateListener.invoke()
      }
      2 -> {
        // "finished" message, even if failed
        finishedListener.invoke()
      }
      3 -> {
        // when file transfer was disrupted or was not
        // properly transferred
        disruptionListener.invoke()
      }
    }
  }

  fun reset(context: Context) {
    context.stopService(
      Intent(context, FileReceiveService::class.java)
    )
  }

  fun receive(session: SessionActivity) {
    val service = Intent(session, FileReceiveService::class.java)
      .putExtra("file_receive", name)
      .putExtra("file_length", length)
      .putExtra("port", port)
      .putExtra("host_address", session.hostAddress)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      session.startForegroundService(service)
    else session.startService(service)
  }

}