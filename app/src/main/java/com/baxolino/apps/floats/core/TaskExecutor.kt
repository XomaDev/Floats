package com.baxolino.apps.floats.core

import android.content.Context
import com.baxolino.apps.floats.core.files.FileRequest
import com.baxolino.apps.floats.core.files.RequestHandler
import com.baxolino.apps.floats.core.transfer.SocketConnection

class TaskExecutor(connection: SocketConnection) {

  private val reader = MultiChannelStream(connection.input)
  private val writer = MultiChannelSystem(connection.output)

  init {
    reader.start()
    writer.start()
  }

  fun execute(context: Context, fileRequest: FileRequest) {
    fileRequest.execute(context, writer)
  }

  // called when Session class is started
  // this looks for incoming file requests that contains
  // the file name followed by the file length
  fun register(handler: RequestHandler) {
    handler.setReader(reader)
  }
}