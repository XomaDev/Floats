package com.baxolino.apps.floats.core.files

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.Observer
import com.baxolino.apps.floats.NsdInterface
import com.baxolino.apps.floats.SessionActivity
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FileReceiver internal constructor(val name: String, val length: Int) {
  interface StartedListener {
    fun started()
  }

  interface UpdateListener {
    fun update(received: Int)
  }

  interface FinishedListener {
    fun finished()
  }

  private var service: NsdInterface? = null

  private lateinit var startListener: () -> Unit

  private var updateListener: UpdateListener? = null
  private var finishedListener: FinishedListener? = null
  private var cancelled = false

  var startTime: Long = 0

  fun setStartListener(listener: () -> Unit) {
    startListener = listener
  }

  fun setUpdateListener(listener: UpdateListener?) {
    updateListener = listener
  }

  fun setFinishedListener(listener: FinishedListener?) {
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

  fun receive(session: SessionActivity) {
    Log.d(TAG, "Receiving")

    val service = Intent(session, FileReceiveService::class.java)
      .putExtra("file_receive", name)
      .putExtra("file_length", length)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      session.startForegroundService(service)
    else session.startService(service)

    Log.d(TAG, "receive: registering")
    FileReceiveService.START_BUS.observe(
      session
    ) {
      Log.d(TAG, "Started receiving")
      session.runOnUiThread(startListener)
    }
    Log.d(TAG, "receive: later invoked")

//    service = object : NsdInterface(context!!) {
//      override fun accepted() {
//        // method not called since we are the one
//        // accepting connection
//      }
//
//      override fun connected(serviceName: String) {
//        Log.d(TAG, "Connected")
//        startListener!!.started()
//        startTime = System.currentTimeMillis()
//        try {
//          receiveContents()
//        } catch (e: IOException) {
//          throw RuntimeException(e)
//        }
//      }
//    }
//    service.discover(name)
  }

//  private fun receiveContents() {
//    val output = DummyOutputStream()
//    val zipInput = GZIPInputStream(service!!.input, Config.BUFFER_SIZE)
//    val buffer = ByteArray(Config.BUFFER_SIZE)
//    var n: Int
//    var received = 0
//    while (!cancelled && zipInput.read(buffer).also { n = it } > 0) {
//      output.write(buffer, 0, n)
//      received += n
//      updateListener!!.update(received)
//    }
//    zipInput.close()
//    service!!.detach()
//    finishedListener!!.finished()
//  }

  companion object {
    private const val TAG = "FileReceiver"
  }
}