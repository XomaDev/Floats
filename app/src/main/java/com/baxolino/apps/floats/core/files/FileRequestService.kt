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
import android.text.format.Formatter
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.core.Config
import com.baxolino.apps.floats.core.http.SocketConnection
import java.io.InputStream
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class FileRequestService : Service() {

  private lateinit var notificationManager: NotificationManager

  private lateinit var connection: SocketConnection

  private var fileLength = 0
  private var notificationId: Int = 8

  private var timeStart = 0L

  private var cancelled = false
  private lateinit var fileNameShort: String

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand()")

    val uri = Uri.parse(intent.getStringExtra("file_uri"))
    val fileName = intent.getStringExtra("file_name")!!
    val localPort = intent.getIntExtra("local_port", -1)

    fileNameShort = FileNameUtil.toShortDisplayName(fileName)

    fileLength = intent.getIntExtra("file_length", -1)

    notificationManager = getSystemService(NotificationManager::class.java) as
            NotificationManager
    notificationId = fileName.hashCode()
    createForeground(notificationId)

    initSocketConnection(uri, localPort)
    return START_NOT_STICKY
  }

  private fun initSocketConnection(uri: Uri, localPort: Int) {
    connection = SocketConnection(localPort)
      .acceptOnPort {
        lookForAbortRequests()
        uploadFileContents(uri)
      }
  }

  private fun createForeground(notificationId: Int) {
    // Create a Notification channel if necessary
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createChannel()
    }
    startForeground(notificationId, buildNotification(0, ""))
  }

  private fun uploadFileContents(uri: Uri) {
    Log.d(TAG, "uploadFileContents()")
    val input = contentResolver.openInputStream(
      uri
    )!!

    timeStart = System.currentTimeMillis()

    val zipOutputStream = GZIPOutputStream(connection.output)

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

    connection.close()
    onComplete()
  }

  private fun onUpdateInfoRequired(written: Int) {
    var speed = ""

    val difference = (System.currentTimeMillis() - timeStart)

    if (difference != 0L) {
      speed = Formatter.formatFileSize(
        applicationContext,
        written.toFloat().div(
          difference.toFloat()
            .div(1000f)
        ).toLong()
      )
    }

    notificationManager.notify(
      notificationId,
      buildNotification(written, speed)
    )
  }

  private fun buildNotification(progress: Int, speed: String): Notification {
    val remoteLayout = RemoteViews(packageName, R.layout.notification_progress)
    remoteLayout.setProgressBar(
      R.id.progress_notification,
      fileLength, progress, false
    )

    remoteLayout.setTextViewText(R.id.filename_notification, fileNameShort)
    if (speed.isNotEmpty())
      remoteLayout.setTextViewText(R.id.speed_notification, speed + "ps")

    return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setStyle(NotificationCompat.DecoratedCustomViewStyle())
      .setCustomContentView(remoteLayout)
      .setOngoing(true)
      .build()
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
    val input: InputStream = connection.input
    val service = Executors.newScheduledThreadPool(1)

    service.scheduleAtFixedRate({
      if (input.available() > 0) {
        Log.d(TAG, "Transfer was cancelled")

        input.read()
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