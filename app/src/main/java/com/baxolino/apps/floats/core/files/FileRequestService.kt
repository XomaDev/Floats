package com.baxolino.apps.floats.core.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.NsdInterface
import com.baxolino.apps.floats.core.Config
import java.io.InputStream
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class FileRequestService : Service() {

  private lateinit var notificationManager: NotificationManager

  private lateinit var nsdService: NsdInterface

  private var fileLength = 0
  private var notificationId: Int = 8

  private var cancelled = false

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand()")

    val uri = Uri.parse(intent.getStringExtra("file_uri"))
    val fileName = intent.getStringExtra("file_name")
    fileLength = intent.getIntExtra("file_length", -1)

    notificationManager = getSystemService(NotificationManager::class.java) as
            NotificationManager
    notificationId = fileName.hashCode()
    createForeground(notificationId)

    requestNsdTransfer(uri, fileName!!)

    return START_NOT_STICKY
  }

  private fun requestNsdTransfer(uri: Uri, fileName: String) {
    nsdService = object : NsdInterface(applicationContext) {
      override fun accepted() {
        // the other device found the service and now is connected
        Log.d(TAG, "accepted()")
        lookForAbortRequests()
        uploadFileContents(uri)
      }

      override fun connected(serviceName: String) {
        // not invoked, since we are not the one making the request
      }
    }
    // register as the name of the file
    // register as the name of the file
    nsdService.registerService(fileName)
  }

  private fun createForeground(notificationId: Int) {
    // Create a Notification channel if necessary
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createChannel()
    }
    startForeground(notificationId, buildNotification(0))
  }

  private fun buildNotification(progress: Int): Notification {
    val title = "Transferring file"

    return NotificationCompat.Builder(applicationContext,
      NOTIF_CHANNEL_ID
    )
      .setContentTitle(title)
      .setTicker(title)
      .setContentText("Transmission in progress")
      .setSmallIcon(android.R.drawable.ic_menu_upload)
      .setProgress(fileLength, progress, false)
      .setOngoing(true)
      // Add the cancel action to the notification which can
      // be used to cancel the worker
      .build()
  }

  private fun uploadFileContents(uri: Uri) {
    Log.d(TAG, "uploadFileContents()")
    val input = contentResolver.openInputStream(
      uri
    )!!

    val zipOutputStream = GZIPOutputStream(nsdService.output)

    val buffer = ByteArray(Config.BUFFER_SIZE)

    var n = 0
    var written = 0

    // read the buffer from the file and write them to sender
    while (!cancelled && input.read(buffer).also { n = it } > 0) {
      try {
        zipOutputStream.write(buffer, 0, n)
        written += n
        onUpdateInfoRequired(written)
      } catch (e: SocketException) {
        // BrokenPipe: this error is thrown when the receiver abruptly
        // closes the connection, we have few ms before the client is fully closed
        Log.d(TAG, "Failed while uploading file contents")
        break
      }
    }

    input.close()

    if (!cancelled) {
      zipOutputStream.finish()
      zipOutputStream.close()
    }

    nsdService.unregister()
    onComplete()
  }

  private fun onUpdateInfoRequired(written: Int) {
    notificationManager.notify(notificationId,
      buildNotification(written))

    // TODO:
    //  notify the activity and do the necessary UI
    //  changes
  }

  private fun onComplete() {
    Handler(mainLooper).post {
      if (cancelled) {
        Toast.makeText(
          this, "File transfer was cancelled.",
          Toast.LENGTH_LONG
        ).show()
      } else {
        Toast.makeText(
          this, "File was transferred",
          Toast.LENGTH_LONG
        ).show()
      }
    }

    // inform the user completion and stop the foreground service
    stopForeground(STOP_FOREGROUND_REMOVE)
  }

  private fun lookForAbortRequests() {
    val input: InputStream = nsdService.input
    val service = Executors.newScheduledThreadPool(1)

    service.scheduleAtFixedRate({
      if (input.available() > 0) {
        Log.d(TAG, "Transfer was cancelled")
        cancelled = true
        service.shutdownNow()
      }
    }, 0, 20, TimeUnit.MILLISECONDS)
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

  override fun onBind(intent: Intent): IBinder? {
    return null
  }

  companion object {
    private const val TAG = "FileRequestService"

    private const val NOTIF_CHANNEL_ID = "I/O Transmission"
    private const val NOTIF_CHANNEL_NAME = "File Transfer"
  }
}