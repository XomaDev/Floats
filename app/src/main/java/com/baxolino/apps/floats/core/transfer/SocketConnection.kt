package com.baxolino.apps.floats.core.transfer

import android.util.Log
import com.baxolino.apps.floats.core.Config
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class SocketConnection(private val localPort: Int) {

  companion object {
    private const val TAG = "SocketConnection"

    private var mainSocket: SocketConnection? = null

    fun getMainInstance(localPort: Int): SocketConnection {
      mainSocket?.let {
        return it
      }
      SocketConnection(localPort)
        .apply {
          mainSocket = this
          return this
        }
    }
  }

  lateinit var socket: Socket

  lateinit var input: InputStream
  lateinit var output: OutputStream

  fun acceptOnPort(onConnect: () -> Unit): SocketConnection {
    thread {
      val serverSocket = ServerSocket(localPort)
      socket = serverSocket.accept()

      Log.d(TAG, "acceptOnPort() Connection was accepted.")

      onConnected(onConnect)
    }
    return this
  }

  fun connectOnPort(port: Int, host: String, onConnect: () -> Unit): SocketConnection {
    thread {
      socket = Socket(host, port)

      Log.d(TAG, "acceptOnPort() Connection was established.")
      onConnected(onConnect)
    }
    return this
  }

  private fun onConnected(onFinish: () -> Unit) {
    socket.apply {
      keepAlive = true
      input = getInputStream()
      output = getOutputStream()

      // this sends the urgent messages to the normal
      // input stream
      if (!oobInline)
        oobInline = true

      sendBufferSize = Config.BUFFER_SIZE
      receiveBufferSize = Config.BUFFER_SIZE
    }
    onFinish.invoke()
  }

  fun close() {
    socket.close()
  }
}