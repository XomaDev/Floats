package com.baxolino.apps.floats.core.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.core.Info
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.tools.ThemeHelper

class FileRequestService : Service() {

  companion object {
    private const val TAG = "FileRequestService"

    private const val NOTIF_CHANNEL_ID = "I/O Transmission"
    private const val NOTIF_CHANNEL_NAME = "File Transfer"

    const val CANCEL_REQUEST_ACTION = "request_cancel"

    class CancelRequestListener(private val service: FileRequestService) : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received Cancel Post Request")
        service.onCancelled()
      }
    }
  }

  private lateinit var notificationManager: NotificationManager

  private lateinit var connection: SocketConnection

  private var fileLength = 0
  private var notificationId: Int = 8

  private var timeStart = 0L

  private var cancelled = false

  private lateinit var fileName: String
  private lateinit var fileNameShort: String

  private val cancelRequestReceiver = CancelRequestListener(this)

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand()")

    val uri = Uri.parse(intent.getStringExtra("file_uri"))
    fileName = intent.getStringExtra("file_name")!!
    val localPort = intent.getIntExtra("local_port", -1)

    fileNameShort = FileNameUtil.toShortDisplayName(fileName)

    fileLength = intent.getIntExtra("file_length", -1)

    notificationManager = getSystemService(NotificationManager::class.java)
    notificationId = fileName.hashCode()

    createForeground(notificationId)
    registerReceiver(cancelRequestReceiver, IntentFilter(CANCEL_REQUEST_ACTION))

    initSocketConnection(uri, localPort)
    return START_NOT_STICKY
  }

  private fun initSocketConnection(uri: Uri, localPort: Int) {
    connection = SocketConnection(localPort)
      .acceptOnPort {
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

    val output = connection.output
    val buffer = ByteArray(Info.BUFFER_SIZE)

    var nread = -1
    var wrote = 0
    while (!cancelled && input.read(buffer).also { nread = it } > 0) {
      output.write(buffer, 0, nread)

      wrote += nread
      onUpdateProgressInfo(wrote)
    }
    output.close()
    input.close()

    connection.close()
    onComplete()
  }

  private fun onCancelled() {
    cancelled = true
  }

  private fun onUpdateProgressInfo(written: Int) {
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
    val remoteLayout = ThemeHelper.getProgressBar(this)
    remoteLayout.setProgressBar(
      R.id.progress_notification,
      fileLength, progress, false
    )

    remoteLayout.setTextViewText(R.id.filename_notification, fileNameShort)
    if (speed.isNotEmpty())
      remoteLayout.setTextViewText(R.id.speed_notification, speed + "ps")

    return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
      .setSmallIcon(R.mipmap.upload)
      .setStyle(NotificationCompat.DecoratedCustomViewStyle())
      .setColor(ThemeHelper.variant70Color(this))
      .setCustomContentView(remoteLayout)
      .setOngoing(true)
      .build()
  }

  private fun onComplete() {
    Handler(mainLooper).post {
      if (cancelled) {
        Toast.makeText(
          this, "File transfer was was cancelled.",
          Toast.LENGTH_LONG
        ).show()
      } else {
        Toast.makeText(
          this, "File was transferred",
          Toast.LENGTH_LONG
        ).show()
      }
    }
    unregisterWithStop()
  }

  private fun unregisterWithStop() {

    if (cancelled) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        .setSmallIcon(R.mipmap.check)
        .setContentTitle("File sent")
        .setContentText("$fileName was sent.")
        .setColor(ThemeHelper.variant70Color(this))
        .build()
      notificationManager.notify(notificationId, notification)

      stopForeground(STOP_FOREGROUND_DETACH)
    }
    unregisterReceiver(cancelRequestReceiver)
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
}