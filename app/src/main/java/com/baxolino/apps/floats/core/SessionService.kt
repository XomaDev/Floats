package com.baxolino.apps.floats.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.SessionActivity
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.tools.DynamicTheme
import io.paperdb.Paper
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


class SessionService : Service() {

  companion object {
    private const val TAG = "SessionService"

    private const val NOTIF_CHANNEL_ID = "Session"
    private const val NOTIF_CHANNEL_NAME = "Transfer Session"

    private const val HEARTBEAT_MESSAGE = 0
    private const val MANUAL_DISCONNECT_MESSAGE = 1

    const val DISCONNECT_BROADCAST_ACTION = "disconnect_broadcast"
    private const val DISCONNECT_BUTTON_BROADCAST_ACTION = "disconnect_button_broadcast"
  }

  private lateinit var partner: String
  private lateinit var host: String

  private val connection = SocketConnection()

  private lateinit var input: InputStream
  private lateinit var output: OutputStream

  private lateinit var manager: NotificationManager

  private val disconnectButtonReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "Disconnect Button Clicked")
      thread {
        output.write(
          MANUAL_DISCONNECT_MESSAGE
        )
        onDisconnect()
      }
    }
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    manager = getSystemService(NotificationManager::class.java)

    partner = intent.getStringExtra("partner_device")!!
    host = intent.getStringExtra("host")!!

    Log.d(TAG, "Session with $partner")

    val isServer = intent.getBooleanExtra("server", false)

    val port = intent.getIntExtra("port", -1)
    if (isServer) {
      connection.acceptOnPort(
        port
      ) {
        Log.d(TAG, "Accepted()")
        init()
      }
    } else {
      val executor = ScheduledThreadPoolExecutor(1)
      executor.schedule({
        connection.connectOnPort(port, host, retry = true) {
          Log.d(TAG, "Connected()")
          executor.shutdownNow()
          init()
        }
      }, 1, TimeUnit.SECONDS)
    }
    return START_NOT_STICKY
  }

  private fun init() {
    Paper.init(this)

    input = connection.input
    output = connection.output

    var lastHeartBeat = System.currentTimeMillis()

    val executor = ScheduledThreadPoolExecutor(2)
    executor.scheduleAtFixedRate({
      if (input.available() > 0) {
        when (input.read()) {
          HEARTBEAT_MESSAGE -> {
            Log.d(TAG, "Beat")
            lastHeartBeat = System.currentTimeMillis()

            Paper.book()
              .write("last_beat", lastHeartBeat)
          }

          MANUAL_DISCONNECT_MESSAGE -> {
            Log.d(TAG, "Manual disconnect")
            executor.shutdownNow()

            onDisconnect()
          }
        }
      }
    }, 0, 1, TimeUnit.MILLISECONDS)

    executor.scheduleAtFixedRate({
      if ((System.currentTimeMillis() - lastHeartBeat) >= 5000) {
        Log.d(TAG, "Stopped receiving heart beats")
        onDisconnect()

        executor.shutdownNow()
      }
      try {
        output.write(
          HEARTBEAT_MESSAGE
        )
      } catch (e: SocketException) {
        Log.d(TAG, "Stopped receiving heart beats")

        // this could be due to unexpected shutdown
        onDisconnect()
        executor.shutdownNow()
      }
    }, 0, 2, TimeUnit.SECONDS)

    foreground()
  }

  private fun onDisconnect() {
    connection.close()
    stop()
    // send the disconnect broadcast
    // action
    sendBroadcast(
      Intent(
        DISCONNECT_BROADCAST_ACTION
      )
    )
    stopSelf()
  }

  private fun stop() {
    stopForeground(STOP_FOREGROUND_REMOVE)
    unregisterReceiver(disconnectButtonReceiver)
  }

  private fun foreground() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      createChannel()

    registerReceiver(
      disconnectButtonReceiver,
      IntentFilter(DISCONNECT_BUTTON_BROADCAST_ACTION)
    )

    val disconnectPendingIntent = PendingIntent.getBroadcast(
      this,
      0,
      Intent(DISCONNECT_BUTTON_BROADCAST_ACTION),
      PendingIntent.FLAG_IMMUTABLE
    )


    val sessionIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, SessionActivity::class.java)
          .setFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
          )
          .putExtra("deviceName", partner)
          .putExtra("hostAddress", host),
        PendingIntent.FLAG_MUTABLE
      )

    val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
      .setSmallIcon(R.mipmap.broadcast)
      .setContentTitle("Device connected")
      .setContentText("Connected to $partner")
      .setContentIntent(sessionIntent)
      .addAction(R.mipmap.x_lg, "Disconnect", disconnectPendingIntent)
      .setColor(DynamicTheme.variant70Color(this))
      .setOngoing(true)
      .build()
    startForeground(
      10101, notification
    )
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun createChannel() {
    val serviceChannel = NotificationChannel(
      NOTIF_CHANNEL_ID,
      NOTIF_CHANNEL_NAME,
      NotificationManager.IMPORTANCE_HIGH
    )
    manager.createNotificationChannel(serviceChannel)
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
}