package com.baxolino.apps.floats.core.files.interfaces

import android.os.Bundle
import com.baxolino.apps.floats.core.files.MessageReceiver

class ReceiveInterface {

  companion object {
    var staticUpdateInterface: ReceiveInterface? = null

    fun getUpdateInterface(): ReceiveInterface {
      if (staticUpdateInterface == null) {
        return ReceiveInterface()
      }
      return staticUpdateInterface!!
    }
  }

  private lateinit var startListener: () -> Unit

  private lateinit var updateListener: () -> Unit
  private lateinit var finishedListener: () -> Unit

  private lateinit var disruptionListener: () -> Unit

  var fileName = ""
  var fileLength = -1

  private var startTime = 0L

  var progress = 0

  // formatted transfer speed like 7 Mb(ps)
  var transferSpeed = ""

  init {
    MessageReceiver.receiveListener = { what, arg1, data ->
      onMessageReceived(what, arg1, data)
    }
    staticUpdateInterface = this
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

  private fun onMessageReceived(what: Int, arg1: Int, data: Bundle) {
    when (what) {
      0 -> {
        // "started" message
        startTime = data.getLong("time")

        fileName = data.getString("fileName")!!
        fileLength = data.getInt("fileLength")

        startListener.invoke()
      }
      1 -> {
        // "update" message
        progress = arg1
        transferSpeed = data.getString("speed", "")

        updateListener.invoke()
      }
      2 -> {
        fileLength = -1
        fileName = ""
        // "finished" message, even if failed
        finishedListener.invoke()
      }
      3 -> {
        fileLength = -1
        fileName = ""
        // when file transfer was disrupted or was not
        // properly transferred
        disruptionListener.invoke()
      }
    }
  }
}