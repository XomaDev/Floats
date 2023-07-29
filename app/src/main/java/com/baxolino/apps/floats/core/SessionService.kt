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
import com.baxolino.apps.floats.HomeActivity
import com.baxolino.apps.floats.HomeActivity.Companion.RESTORE_SESSION_CHECK
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.SessionActivity
import com.baxolino.apps.floats.core.files.FileReceiveService
import com.baxolino.apps.floats.core.files.FileReceiveService.Companion.CANCEL_RECEIVE_ACTION
import com.baxolino.apps.floats.core.files.FileRequest
import com.baxolino.apps.floats.core.files.FileRequestService
import com.baxolino.apps.floats.core.files.FileRequestService.Companion.CANCEL_REQUEST_ACTION
import com.baxolino.apps.floats.core.files.MessageReceiver
import com.baxolino.apps.floats.core.files.RequestHandler
import com.baxolino.apps.floats.core.transfer.ChannelInfo
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.tools.DynamicTheme
import io.paperdb.Paper
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class SessionService : Service() {

  companion object {
    private const val TAG = "SessionService"

    private const val NOTIF_CHANNEL_ID = "Session"
    private const val NOTIF_CHANNEL_NAME = "Transfer Session"

    private const val HEARTBEAT_MESSAGE = 0
    private const val MANUAL_DISCONNECT_MESSAGE = 1

    const val DISCONNECT_BROADCAST_ACTION = "disconnect_broadcast"
    const val TRANSMISSION_BROADCAST_ACTION = "transmission_broadcast"
    const val DISCONNECT_BUTTON_BROADCAST_ACTION = "disconnect_button_broadcast"
  }

  private lateinit var partner: String
  lateinit var host: String

  private val connection = SocketConnection()
  private lateinit var executor: TaskExecutor

  private lateinit var input: InputStream
  private lateinit var output: OutputStream

  private lateinit var manager: NotificationManager

  private val disconnectButtonReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "Disconnect Button Clicked")
      executor.respond(
        ChannelInfo.SMALL_DATA_EXCHANGE_CHANNEL_INFO,
        MANUAL_DISCONNECT_MESSAGE.toByte()
      )
      sendBroadcast(Intent(FileReceiveService.CANCEL_RECEIVE_ACTION))

      // the executor should first fully finish writing
      // the data, after that, we close :)
      val poolExecutor = ScheduledThreadPoolExecutor(1)
      poolExecutor.schedule({
        onDisconnect()
        poolExecutor.shutdown()
      }, 140, TimeUnit.MILLISECONDS)
    }
  }

  // when the app was completely closed, we are responsible to
  // restore it back to the previous session scree

  private val restoreSessionRequestReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "Received restore request")
      // when we receive a request, we are going to reply back to
      // it with things like host address, and connected device name
      sendBroadcast(
        Intent(
          HomeActivity.RESTORE_SESSION_REPLY
        )
          .putExtra("deviceName", partner)
          .putExtra("hostAddress", host)
      )
    }
  }

  private val transferFileRequest = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
      when (intent.getIntExtra("what", -1)) {
        0 -> {
          val fileName = intent.getStringExtra("file_name")!!

          Log.d(TAG, "Initiating file transfer $fileName")
          val fileRequest = FileRequest(
            intent.getStringExtra("file_uri")!!,
            fileName,
            intent.getIntExtra("file_length", -1)
          )

          executor.execute(
            this@SessionService,
            fileRequest
          )
        }

        1 -> {
          executor.respond(ChannelInfo.CANCEL_REQUEST_CHANNEL_INFO)
        }
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

    executor = TaskExecutor(connection)

    var lastHeartBeat = System.currentTimeMillis()
    executor.register(ChannelInfo.SMALL_DATA_EXCHANGE_CHANNEL_INFO, false) {
      when (it.toInt()) {
        HEARTBEAT_MESSAGE -> {
          Log.d(TAG, "Beat")
          lastHeartBeat = System.currentTimeMillis()

          Paper.book()
            .write("last_beat", lastHeartBeat)
        }

        MANUAL_DISCONNECT_MESSAGE -> {
          Log.d(TAG, "Manual disconnect")
          onDisconnect()
        }
      }
    }

    val poolExecutor = ScheduledThreadPoolExecutor(1)
    poolExecutor.scheduleAtFixedRate({
      if ((System.currentTimeMillis() - lastHeartBeat) >= 5000) {
        Log.d(TAG, "Stopped receiving heart beats")
        onDisconnect()

        poolExecutor.shutdownNow()
      }
      try {
        executor.respond(
          ChannelInfo.SMALL_DATA_EXCHANGE_CHANNEL_INFO,
          HEARTBEAT_MESSAGE.toByte()
        )
      } catch (e: SocketException) {
        Log.d(TAG, "Stopped receiving heart beats")

        // this could be due to unexpected shutdown
        onDisconnect()
        poolExecutor.shutdownNow()
      }
    }, 0, 2, TimeUnit.SECONDS)

    executor.register(RequestHandler { receiver ->
      Log.d(TAG, "File Receive Request = ${receiver.name}")
      receiver.receive(this, partner)
    })

    foreground()
  }

  private fun onDisconnect() {
    stop()
    // send the disconnect broadcast
    // action
    sendBroadcast(
      Intent(
        DISCONNECT_BROADCAST_ACTION
      )
    )

    sendBroadcast(
      Intent(
        CANCEL_RECEIVE_ACTION
      )
    )
    stopService(
      Intent(
        this, FileReceiveService::class.java
      )
    )

    sendBroadcast(
      Intent(
        CANCEL_REQUEST_ACTION
      )
    )
    stopService(
      Intent(
        this, FileRequestService::class.java
      )
    )
    stopSelf()
  }

  private fun stop() {
    executor.stopStreams()
    connection.close()

    stopForeground(STOP_FOREGROUND_REMOVE)

    MessageReceiver.onStop(this)

    unregisterReceiver(transferFileRequest)
    unregisterReceiver(disconnectButtonReceiver)
    unregisterReceiver(restoreSessionRequestReceiver)
  }

  private fun foreground() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      createChannel()

    MessageReceiver.onStart(this)

    registerReceiver(
      transferFileRequest,
      IntentFilter(TRANSMISSION_BROADCAST_ACTION)
    )
    registerReceiver(
      disconnectButtonReceiver,
      IntentFilter(DISCONNECT_BUTTON_BROADCAST_ACTION)
    )
    registerReceiver(
      restoreSessionRequestReceiver,
      IntentFilter(RESTORE_SESSION_CHECK)
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

  override fun onBind(intent: Intent?): IBinder? = null
}