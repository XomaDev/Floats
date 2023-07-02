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
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.os.Handler
import android.os.IBinder
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.core.files.MessageReceiver.Companion.RECEIVE_ACTION
import com.baxolino.apps.floats.tools.DynamicTheme
import io.paperdb.Paper
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread
import kotlin.random.Random

class FileReceiveService : Service() {

  companion object {
    private const val TAG = "FileReceiveService"

    private const val NOTIF_CHANNEL_ID = "I/O Transmission"
    private const val NOTIF_CHANNEL_NAME = "File Transfer"

    const val CANCEL_RECEIVE_ACTION = "cancel_receive"

    class CancelReceiveListener(private val service: FileReceiveService) : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received Cancel Request")
        service.cancelled()
      }
    }
  }

  private lateinit var notificationManager: NotificationManager


  private var notificationId: Int = 7
  private var fileLength = 0

  private lateinit var fileName: String
  private lateinit var fileNameShort: String

  private lateinit var from: String

  private var timeStart = 0L
  private var cancelled = false

  private var hasStopped = false

  private val cancelReceiveListener = CancelReceiveListener(this)

  init {
    System.loadLibrary("native-lib")
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand()")

    // init the paper db
    Paper.init(this)

    from = intent.getStringExtra("from")!!
    fileName = intent.getStringExtra("file_receive")!!
    fileNameShort = FileNameUtil.toShortDisplayName(fileName)

    fileLength = intent.getIntExtra("file_length", -1)

    if (fileLength == -1) {
      Log.d(TAG, "File Length is Zero")
      // this should not happen
      return START_NOT_STICKY
    }

    notificationManager = getSystemService(NotificationManager::class.java) as
            NotificationManager

    notificationId = fileName.hashCode()
    createForeground(notificationId)

    registerReceiver(cancelReceiveListener, IntentFilter(CANCEL_RECEIVE_ACTION))

    val port = intent.getIntExtra("port", -1)
    val hostAddress = intent.getStringExtra("host_address")!!

    thread {
      initSocketConnection(port, hostAddress)
    }
    return START_NOT_STICKY
  }

  private fun initSocketConnection(port: Int, host: String) {
    val externalStorageDir = getExternalStoragePublicDirectory(
      Environment.DIRECTORY_DOCUMENTS)
    var file = File(
      externalStorageDir, fileName
    )
    var saveFileName = fileName
    var count = 1
    while (file.exists()) {
      // if there is already a file, like hello.txt, it'll save it as
      // (1) hello.txt
      saveFileName = "($count) $fileName"
      file = File(
        externalStorageDir, saveFileName
      )
      count++
    }
    Log.d(TAG, "Path = $file")
    val result = receiveFile(
      externalStorageDir.absolutePath,
      saveFileName,
      fileLength,
      host, port
    )
    if ("success" in result) {
      Log.d(TAG, "Success! ${file.length()} res_message: $result")

      val finalFilePath = result.substring(11)
      Log.d(TAG, "Saved at path $finalFilePath")

      val savedFile = File(finalFilePath)

      val data = JSONObject()
        .put("path", savedFile.absolutePath)
        .put("size", fileLength)
        .put("time", System.currentTimeMillis())
        .put("from", from)
      Paper.book("files")
        .write(savedFile.name, data.toString())
    } else {
      cancelled = true

      // let them know, we failed
      message(3)

      Log.d(TAG, "Message: $result")
    }
    onComplete()
  }

  private external fun receiveFile(
    outputDir: String,
    fileName: String,
    expectedSize: Int,
    host: String,
    port: Int
  ): String


  // called by the native class
  @Suppress("unused")
  fun onStart() {
    Log.d(TAG, "Started")

    timeStart = System.currentTimeMillis()

    // let them know, we are starting
    message(0, -1, Bundle().apply {
      putLong("time", timeStart)
      putString("fileName", fileName)
      putInt("fileLength", fileLength)
    })
  }


  // this is called by the native file receiver class

  @Suppress("unused")
  fun update(received: Int) {
    if (hasStopped)
      return
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
    message(1, progress, Bundle().apply {
      putString("speed", speed)
    })
  }

  private external fun cancelFileReceive()

  @Suppress("unused")
  fun onCancelledNative() {
    Log.d(TAG, "Received Native Cancel Callback")
  }

  // called by CancelRequestReceiver
  private fun cancelled() {
    cancelled = true

    // cancels any ongoing file receiving
    cancelFileReceive()
    unregisterWithStop()
  }

  private fun onComplete() {
    // inform the user completion and stop the foreground service
    Handler(mainLooper).post {
      Toast.makeText(
        this,
        if (cancelled) "File transfer was disrupted."
        else "File was transferred",
        Toast.LENGTH_LONG
      ).show()
    }

    message(2)
    unregisterWithStop()
  }

  private fun unregisterWithStop() {
    if (hasStopped)
      return
    hasStopped = true

    unregisterReceiver(cancelReceiveListener)
    stopForeground(STOP_FOREGROUND_REMOVE)

    if (!cancelled) {
      val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        .setSmallIcon(R.mipmap.check)
        .setContentTitle("File received")
        .setContentText("$fileName was received.")
        .setColor(DynamicTheme.variant70Color(this))
        .build()
      notificationManager.notify(Random.nextInt(), notification)
    }

    stopSelf()
  }

  private fun message(what: Int) {
    message(what, -1)
  }

  private fun message(what: Int, arg1: Int) {
    message(what, arg1, Bundle())
  }

  private fun message(what: Int, arg1: Int, data: Bundle) {
    sendBroadcast(
      Intent(RECEIVE_ACTION)
        .putExtra("type", 0)
        .putExtra("what", what)
        .putExtra("arg1", arg1)
        .putExtra("bundle_data", data)
    )
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
    val remoteLayout = DynamicTheme.getProgressBar(this)
    remoteLayout.setProgressBar(
      R.id.progress_notification,
      fileLength, progress, false
    )

    remoteLayout.setTextViewText(R.id.filename_notification, fileNameShort)
    if (speed.isNotEmpty())
      remoteLayout.setTextViewText(R.id.speed_notification, speed + "ps")

    return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
      .setSmallIcon(R.mipmap.download)
      .setStyle(NotificationCompat.DecoratedCustomViewStyle())
      .setColor(DynamicTheme.variant70Color(this))
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