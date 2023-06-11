package com.baxolino.apps.floats.core

import android.content.Context
import com.baxolino.apps.floats.core.files.FileRequest
import com.baxolino.apps.floats.core.files.RequestHandler
import com.baxolino.apps.floats.core.io.BitStream
import com.baxolino.apps.floats.core.io.DataInputStream
import com.baxolino.apps.floats.core.transfer.SocketConnection

class TaskExecutor(connection: SocketConnection) {

  val reader = MultiChannelStream(connection.input)
  val writer = MultiChannelSystem(connection.output)

  init {
    reader.start()
    writer.start()
  }

  fun execute(context: Context, fileRequest: FileRequest) {
    fileRequest.execute(context, this)
  }

  // called when Session class is started
  // this looks for incoming file requests that contains
  // the file name followed by the file length
  fun register(handler: RequestHandler) {
    handler.setReader(reader)
  }

  fun writeCanel(port: Int) {
    writer.write(
      ChannelInfo(
        BitStream()
          .writeInt32(port)
          .toBytes()
      ),
      ByteArray(1)
    )
  }

  fun registerOnCancel(port: Int, listener: () -> Unit) {
    val dataInputStream = DataInputStream()

    val channel = ChannelInfo(
      BitStream()
        .writeInt32(port)
        .toBytes()
    )
    reader.registerChannelStream(
      channel,
      dataInputStream
    )

    dataInputStream.setByteListener {
      listener.invoke()
      reader.forget(channel)
      return@setByteListener true
    }
  }
}