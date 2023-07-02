package com.baxolino.apps.floats.core.files

import android.content.Intent
import android.os.Build
import com.baxolino.apps.floats.SessionActivity


class FileReceiver internal constructor(private val port: Int, val name: String, val length: Int) {

  fun receive(session: SessionActivity, from: String) {
    val service = Intent(session, FileReceiveService::class.java)
      .putExtra("file_receive", name)
      .putExtra("file_length", length)
      .putExtra("port", port)
      .putExtra("host_address", session.hostAddress)
      .putExtra("from", from)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      session.startForegroundService(service)
    else session.startService(service)
  }

}