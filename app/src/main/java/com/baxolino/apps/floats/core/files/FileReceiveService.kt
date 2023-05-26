package com.baxolino.apps.floats.core.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.NsdInterface
import com.baxolino.apps.floats.core.Config
import com.baxolino.apps.floats.core.bytes.io.DummyOutputStream
import java.util.zip.GZIPInputStream

class FileReceiveService : Service() {

  companion object {
    private const val TAG = "FileReceiveService"

    private const val NOTIF_CHANNEL_ID = "I/O Transmission"
    private const val NOTIF_CHANNEL_NAME = "File Transfer"
  }

  private lateinit var notificationManager: NotificationManager

  private lateinit var nsdService: NsdInterface
  private lateinit var messenger: Messenger

  private var notificationId: Int = 7
  private var fileLength = 0


  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    val fileName = intent.getStringExtra("file_receive")!!
    fileLength = intent.getIntExtra("file_length", -1)

    if (fileLength == -1) {
      Log.d(TAG, "File Length is Zero")
      // this should not happen
      return START_NOT_STICKY
    }
    messenger = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra("handler", Messenger::class.java)!!
    } else {
      intent.getParcelableExtra("handler")!!
    }

    notificationManager = getSystemService(NotificationManager::class.java) as
            NotificationManager

    notificationId = fileName.hashCode()
    createForeground(notificationId)

    initiateDiscovery(fileName)
    return START_NOT_STICKY
  }

  private fun initiateDiscovery(name: String) {
    nsdService = object : NsdInterface(applicationContext) {
      override fun accepted() {
        // method not called since we are the one
        // accepting connection
      }

      override fun connected(serviceName: String) {
        Log.d(TAG, "Connected")
        receiveContents()
      }
    }
    nsdService.discover(name)
  }

  private fun receiveContents() {
    // send a message that receive is started through code 0
    messenger.send(
      Message.obtain().apply {
        what = 0
        data.putLong("time", System.currentTimeMillis())
      }
    )

    val output = DummyOutputStream()
    val zipInput = GZIPInputStream(nsdService.input, Config.BUFFER_SIZE)
    val buffer = ByteArray(Config.BUFFER_SIZE)

    var n: Int
    var received = 0
    while (zipInput.read(buffer).also { n = it } > 0) {
      output.write(buffer, 0, n)
      received += n

      notificationManager.notify(notificationId, buildNotification(received))
      messenger.send(
        Message.obtain().apply {
          what = 1
          arg1 = received
        }
      )
    }
    zipInput.close()
    nsdService.detach()

    onComplete()
  }

  private fun onComplete() {
    messenger.send(
      Message.obtain().apply {
        what = 2
      }
    )

    // inform the user completion and stop the foreground service
    stopForeground(STOP_FOREGROUND_REMOVE)
  }

  private fun createForeground(notificationId: Int) {
    // Create a Notification channel if necessary
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createChannel()
    }

    val notification = buildNotification(0)
    Log.d(TAG, "Notif = $notificationId")
    startForeground(notificationId, notification)
  }

  private fun buildNotification(progress: Int): Notification {
    return NotificationCompat.Builder(
      applicationContext,
      NOTIF_CHANNEL_ID
    )
      .setContentTitle("Receiving file")
      .setContentText("Receiving in progress")
      .setSmallIcon(android.R.drawable.ic_menu_save)
      .setProgress(fileLength, progress, false)
      .setOngoing(true)
      // Add the cancel action to the notification which can
      // be used to cancel the worker
      .build()
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun createChannel() {
    val serviceChannel = NotificationChannel(
      NOTIF_CHANNEL_ID,
      NOTIF_CHANNEL_NAME,
      NotificationManager.IMPORTANCE_DEFAULT
    )
    notificationManager.createNotificationChannel(serviceChannel)
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
}