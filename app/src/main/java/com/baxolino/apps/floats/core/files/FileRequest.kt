package com.baxolino.apps.floats.core.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.baxolino.apps.floats.core.Channel
import com.baxolino.apps.floats.core.MultiChannelSystem
import com.baxolino.apps.floats.core.transfer.SocketUtils
import com.baxolino.apps.floats.core.io.BitStream

class FileRequest(
  private val fileInput: Uri,
  private val fileName: String,
  private val fileLength: Int
) {

  init {
    Log.d(TAG, "$TAG($fileInput)")
  }

  fun execute(context: Context, writer: MultiChannelSystem) {
    val localPort = SocketUtils.findAvailableTcpPort()

    val fileNameBytes = fileName.toByteArray()
    val requestData = BitStream()
      .writeInt32(localPort)
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
      .putExtra("local_port", localPort)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      context.startForegroundService(service)
    else context.startService(service)
  }

  companion object {
    private const val TAG = "FileRequest"
  }
}