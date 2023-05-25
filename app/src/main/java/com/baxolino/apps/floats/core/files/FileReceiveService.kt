package com.baxolino.apps.floats.core.files

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.NsdInterface
import com.baxolino.apps.floats.core.Config
import com.baxolino.apps.floats.core.bytes.io.DummyOutputStream
import java.util.zip.GZIPInputStream

class FileReceiveService: Service() {
  private lateinit var notificationManager: NotificationManager

  private lateinit var nsdService: NsdInterface

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    val fileName = intent.getStringExtra("file_receive")!!
    val fileLength = intent.getIntExtra("file_length", -1)

    if (fileLength == -1) {
      Log.d(TAG, "File Length is Zero")
      // this should not happen
      return START_NOT_STICKY
    }
    notificationManager = getSystemService(NotificationManager::class.java) as
            NotificationManager
    createForeground(fileName.hashCode())

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
    val output = DummyOutputStream()
    val zipInput = GZIPInputStream(nsdService.input, Config.BUFFER_SIZE)
    val buffer = ByteArray(Config.BUFFER_SIZE)

    var n: Int
    var received = 0
    while (zipInput.read(buffer).also { n = it } > 0) {
      output.write(buffer, 0, n)
      received += n
    }
    zipInput.close()
    nsdService.detach()

    onComplete()
  }

  private fun onComplete() {
    Toast.makeText(
      this, "File was received",
      Toast.LENGTH_LONG).show()

    // inform the user completion and stop the foreground service
    stopForeground(STOP_FOREGROUND_REMOVE)
  }

  private fun createForeground(notificationId: Int) {
    val title = "Receiving file"

    // Create a Notification channel if necessary
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createChannel()
    }

    val notification = NotificationCompat.Builder(applicationContext,
      NOTIF_CHANNEL_ID
    )
      .setContentTitle(title)
      .setTicker(title)
      .setContentText("Receiving in progress")
      .setSmallIcon(android.R.drawable.ic_menu_save)
      .setOngoing(true)
      // Add the cancel action to the notification which can
      // be used to cancel the worker
      .build()
    Log.d(TAG, "Notif = $notificationId")
    startForeground(notificationId, notification)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun createChannel() {
    val serviceChannel = NotificationChannel(
      NOTIF_CHANNEL_ID,
      NOTIF_CHANNEL_NAME,
      NotificationManager.IMPORTANCE_HIGH
    )
    notificationManager.createNotificationChannel(serviceChannel)
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  companion object {
    private const val TAG = "FileReceiveService"

    private const val NOTIF_CHANNEL_ID = "I/O Transmission"
    private const val NOTIF_CHANNEL_NAME = "File Transfer"
  }
}