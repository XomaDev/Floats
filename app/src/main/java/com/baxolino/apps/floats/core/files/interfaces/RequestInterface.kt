package com.baxolino.apps.floats.core.files.interfaces

import android.os.Bundle
import com.baxolino.apps.floats.core.files.MessageReceiver


// We are going to use this class somewhere in the
// future
class RequestInterface {
  companion object {
    var staticUpdateInterface: RequestInterface? = null

    fun getUpdateInterface(): RequestInterface {
      if (staticUpdateInterface == null) {
        return RequestInterface()
      }
      return staticUpdateInterface!!
    }
  }

  lateinit var startListener: () -> Unit
  lateinit var updateListener: (Int, String) -> Unit

  var fileName = ""
  var fileLength = -1

  private var startTime = 0L

  lateinit var finishedListener: () -> Unit
  lateinit var disruptionListener: () -> Unit

  init {
    MessageReceiver.requestUpdateListener = { what, arg1, data ->
      onMessageReceived(what, arg1, data)
    }
    staticUpdateInterface = this
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
        updateListener.invoke(arg1,
          data.getString("speed", ""))
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