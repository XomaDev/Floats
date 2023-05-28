package com.baxolino.apps.floats.core.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.baxolino.apps.floats.core.Channel
import com.baxolino.apps.floats.core.MultiChannelSystem
import com.baxolino.apps.floats.core.io.BitOutputStream

class FileRequest(
  private val fileInput: Uri,
  private val fileName: String,
  private val fileLength: Int
) {

  init {
    Log.d(TAG, "$TAG($fileInput)")
  }

  fun execute(context: Context, writer: MultiChannelSystem) {
    val fileNameBytes = fileName.toByteArray()
    val requestData = BitOutputStream()
      .writeInt32(fileLength)
      .writeInt32(fileNameBytes.size)
      .write(fileNameBytes)
      .toBytes()
    writer.write(Channel.FILE_REQUEST_CHANNEL, requestData)

    // the uploading world will be executed by the foreground service
    val service = Intent(context, FileRequestService::class.java)
      .putExtra("file_uri", fileInput.toString())
      .putExtra("file_name", fileName)
      .putExtra("file_length", fileLength)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      context.startForegroundService(service)
    else context.startService(service)
  }

  companion object {
    private const val TAG = "FileRequest"
  }
}