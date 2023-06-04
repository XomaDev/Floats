package com.baxolino.apps.floats.core.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.text.format.Formatter
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.core.Config
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.core.io.NullOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class FileReceiveService : Service() {

  companion object {
    private const val TAG = "FileReceiveService"

    private const val NOTIF_CHANNEL_ID = "I/O Transmission"
    private const val NOTIF_CHANNEL_NAME = "File Transfer"

    const val CANCEL_REQUEST_ACTION = "request_cancel"

    class CancelRequestReceiver(private val service: FileReceiveService) : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received Cancel Request")
        service.cancelled()
      }
    }
  }

  private lateinit var notificationManager: NotificationManager

  private lateinit var connection: SocketConnection
  private lateinit var messenger: Messenger

  private var notificationId: Int = 7
  private var fileLength = 0

  private var fileNameShort = ""

  private var timeStart = 0L
  private var cancelled = false

  private val cancelRequestReceiver = CancelRequestReceiver(this)

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand()")

    val fileName = intent.getStringExtra("file_receive")!!
    fileNameShort = FileNameUtil.toShortDisplayName(fileName)

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

    registerReceiver(cancelRequestReceiver, IntentFilter(CANCEL_REQUEST_ACTION))

    val port = intent.getIntExtra("port", -1)
    val hostAddress = intent.getStringExtra("host_address")!!

    initSocketConnection(port, hostAddress)
    return START_NOT_STICKY
  }

  private fun initSocketConnection(port: Int, host: String) {
    // does not matter what we pass to constructor over here
    connection = SocketConnection(0)
      .connectOnPort(port, host) {
        receiveContents()
      }
  }

  private fun receiveContents() {
    // send a message that receive is started through code 0
    timeStart = System.currentTimeMillis()
    messenger.send(
      Message.obtain().apply {
        what = 0
        data.putLong("time", timeStart)
      }
    )

    val output = NullOutputStream()
    val zipInput = GZIPInputStream(connection.input, Config.BUFFER_SIZE)
    val buffer = ByteArray(Config.BUFFER_SIZE)

    var n = 0
    var received = 0
    while (!cancelled && zipInput.read(buffer).also { n = it } > 0) {
      output.write(buffer, 0, n)
      received += n

      onUpdateInfoNeeded(received)
    }
    if (cancelled)
      return
    zipInput.close()
    connection.close()

    onComplete()
  }

  private fun onUpdateInfoNeeded(received: Int) {

    val progress = (received.toFloat().div(fileLength) * 100).toInt()
    var speed = ""

    val difference = (System.currentTimeMillis() - timeStart)

    if (difference != 0L) {
      speed = Formatter.formatFileSize(
        applicationContext,
        received.toFloat().div(
          difference.toFloat()
            .div(1000f)
        ).toLong()
      )
    }
    notificationManager.notify(
      notificationId,
      createNotification(received, speed)
    )

    // this sends the progress and transfer speed
    // to the activity
    messenger.send(
      Message.obtain().apply {
        what = 1
        arg1 = progress
        data.putString("speed", speed)
      }
    )
  }

  // called by CancelRequestReceiver
  private fun cancelled() {
    // send a cancel request to the sender
    Thread {
      // service operators on the main thread
      connection.socket.sendUrgentData(0)
    }.start()
    val executor = Executors.newScheduledThreadPool(1)

    // now stop receiving after 4 ms, by this time, the
    // sender should have stopped adding more data
    executor.schedule({
      cancelled = true

      unregisterWithStop()
    }, 40, TimeUnit.MILLISECONDS)
  }

  private fun onComplete() {
    // inform the user completion and stop the foreground service
    Handler(mainLooper).post {
      Toast.makeText(
        this,
        if (cancelled) "File transfer was cancelled."
        else "File was transferred",
        Toast.LENGTH_LONG
      ).show()
    }

    messenger.send(
      Message.obtain().apply {
        what = 2
      }
    )
    unregisterWithStop()
  }

  private fun unregisterWithStop() {
    stopForeground(STOP_FOREGROUND_REMOVE)

    unregisterReceiver(cancelRequestReceiver)
  }

  private fun createForeground(notificationId: Int) {
    // Create a Notification channel if necessary
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createChannel()
    }

    val notification = createNotification(0, "")
    Log.d(TAG, "Notif = $notificationId")
    startForeground(notificationId, notification)
  }

  private fun createNotification(progress: Int, speed: String): Notification {
    // Get the layouts to use in the custom notification
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