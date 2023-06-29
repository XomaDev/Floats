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
import android.os.Looper
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.core.transfer.Info
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.tools.DynamicTheme
import java.io.InputStream
import kotlin.random.Random

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
    Log.d(TAG, "onStartCommand() $cancelled")

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
    // we have to be ready instantly; so we pre-open it
    val input = contentResolver.openInputStream(uri)!!

    connection = SocketConnection()
      .acceptOnPort(localPort, 12000, {
        uploadFileContents(input)
      }, {
        // connection was timed out; this generally
        // does not happen
        Handler(Looper.getMainLooper()).post {
          Log.d(TAG, "Connection was timed out")
          Toast.makeText(
            this,
            "Failed to send file.",
            Toast.LENGTH_LONG
          ).show()
          cancelled = true
          unregisterWithStop()
        }
      })
    sendBroadcast(
      Intent(
        MessageReceiver.RECEIVE_ACTION
      ).putExtra("type", 1)
    )
  }

  private fun createForeground(notificationId: Int) {
    // Create a Notification channel if necessary
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createChannel()
    }
    startForeground(notificationId, buildNotification(0, ""))
  }

  private fun uploadFileContents(input: InputStream) {
    Log.d(TAG, "uploadFileContents()")

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
    val remoteLayout = DynamicTheme.getProgressBar(this)
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
      .setColor(DynamicTheme.variant70Color(this))
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
    unregisterWithStop()
  }

  private fun unregisterWithStop() {
    unregisterReceiver(cancelRequestReceiver)
    stopForeground(STOP_FOREGROUND_REMOVE)

    if (!cancelled) {
      val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        .setSmallIcon(R.mipmap.check)
        .setContentTitle("File sent")
        .setContentText("$fileName was sent.")
        .setColor(DynamicTheme.variant70Color(this))
        .build()
      notificationManager.notify(Random.nextInt(), notification)
    }

    stopSelf()
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