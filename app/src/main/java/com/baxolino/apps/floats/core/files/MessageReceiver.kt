package com.baxolino.apps.floats.core.files

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log

class MessageReceiver {

  fun onResume(context: Context) {
    context.registerReceiver(receiver, IntentFilter(RECEIVE_ACTION))
  }

  fun onPause(context: Context) {
    context.unregisterReceiver(receiver)
  }

  companion object {
    const val RECEIVE_ACTION = "receive_message"
    var listener: ((Int, Int, Bundle) -> Unit)? = null

    val receiver: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        val what = intent.getIntExtra("what", -1)
        val arg1 = intent.getIntExtra("arg1", -1)
        val data = intent.getBundleExtra("bundle_data")!!

        listener?.invoke(what, arg1, data)
      }
    }
  }
}