package com.baxolino.apps.floats.core.files

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.baxolino.apps.floats.NsdInterface
import com.baxolino.apps.floats.core.bytes.io.BitOutputStream

class FileRequestWorker(context: Context, parameters: WorkerParameters) :
  CoroutineWorker(context, parameters) {

  private val notificationManager =
    context.getSystemService(NotificationManager::class.java) as
            NotificationManager

  private lateinit var nsdService: NsdInterface

  override suspend fun doWork(): Result {
    // the file we will be uploading during the connection
    val file = inputData.getString("file") ?: return Result.failure()
    val fileName = inputData.getString("file_name") ?: return Result.failure()
    val fileLength = inputData.getInt("file_length", -1)

    if (fileLength == -1)
      return Result.failure()

    setForeground(createForegroundInfo(file.hashCode()))
    requestNsdTransfer(file, fileName, fileLength)

    return Result.success()
  }

  private fun requestNsdTransfer(file: String, fileName: String, fileLength: Int) {
    // TODO:
    //  the information that will be sent to the receiver will happen
    //  before we initiate the transfer and will likely be moved to KRSystem

    nsdService = object : NsdInterface(applicationContext) {
      override fun accepted() {
        // the other device found the service and now is connected
        Log.d(TAG, "accepted()")
      }

      override fun connected(serviceName: String) {
        // not invoked, since we are not the one making the request
      }
    }
    // register as the name of the file
    // register as the name of the file
    nsdService.registerService(fileName)

    val fileNameBytes = fileName.toByteArray()

    val requestData = BitOutputStream()
      .writeInt32(fileLength)
      .writeInt32(fileNameBytes.size)
      .write(fileNameBytes)
      .toBytes()

    // TODO:
    //  bring in the functionalities of FileRequest class over here
    //  and lets make it happen
  }

  private fun createForegroundInfo(notificationId: Int): ForegroundInfo {
    val title = "Transferring file"

    // Create a Notification channel if necessary
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createChannel()
    }

    val notification = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL_ID)
      .setContentTitle(title)
      .setTicker(title)
      .setContentText("Transmission in progress")
      .setSmallIcon(android.R.drawable.ic_menu_upload)
      .setOngoing(true)
      // Add the cancel action to the notification which can
      // be used to cancel the worker
      .build()
    return ForegroundInfo(notificationId, notification)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun createChannel() {
    // Create a Notification channel
    val channel = NotificationChannel(NOTIF_CHANNEL_ID, NOTIF_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
    notificationManager.createNotificationChannel(channel)
  }

  companion object {

    private const val TAG = "FileRequestWorker"

    private const val NOTIF_CHANNEL_ID = "I/O Transmission"
    private const val NOTIF_CHANNEL_NAME = "File Transfer"
  }
}