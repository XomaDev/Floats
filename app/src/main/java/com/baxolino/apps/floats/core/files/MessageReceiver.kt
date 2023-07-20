package com.baxolino.apps.floats.core.files

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle

class MessageReceiver {

  companion object {
    fun onStart(context: Context) {
      context.registerReceiver(receiver, IntentFilter(RECEIVE_ACTION))
    }

    fun onStop(context: Context) {
      context.unregisterReceiver(receiver)
    }

    const val RECEIVE_ACTION = "receive_message"

    var receiveListener: ((Int, Int, Bundle) -> Unit)? = null
    var requestListener: (() -> Unit)? = null

    var requestUpdateListener: ((Int, Int, Bundle) -> Unit)? = null

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra("type", -1)) {
          0 -> {
            val what = intent.getIntExtra("what", -1)
            val arg1 = intent.getIntExtra("arg1", -1)
            val data = intent.getBundleExtra("bundle_data")!!

            receiveListener?.invoke(what, arg1, data)
          }
          1 -> {
            requestListener?.invoke()
          }
          2 -> {
            // from FileRequestService
            val what = intent.getIntExtra("what", -1)
            val arg1 = intent.getIntExtra("arg1", -1)
            val data = intent.getBundleExtra("bundle_data")!!

            requestUpdateListener?.invoke(what, arg1, data)
          }
        }
      }
    }
  }
}