package com.baxolino.apps.floats.core.transfer

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread


class SocketConnection {

  companion object {
    private const val TAG = "SocketConnection"

    private var mainSocket: SocketConnection? = null

    fun getMainSocket(): SocketConnection {
      mainSocket?.let {
        return it
      }
      SocketConnection()
        .apply {
          mainSocket = this
          return this
        }
    }

    fun clear() {
      mainSocket = null
    }
  }

  lateinit var socket: Socket

  lateinit var input: InputStream
  lateinit var output: OutputStream

  fun acceptOnPort(localPort: Int, onConnect: () -> Unit) {
    acceptOnPort(localPort, -1, onConnect, null)
  }

  fun acceptOnPort(
    localPort: Int,
    timeout: Int,
    onConnect: () -> Unit,
    onTimeout: (() -> Unit)?
  ): SocketConnection {
    thread {
      val serverSocket = ServerSocket(localPort)
      if (timeout != -1)
        serverSocket.soTimeout = timeout
      try {
        socket = serverSocket.accept()
        Log.d(TAG, "acceptOnPort() Connection was accepted.")
        onConnected(onConnect)
      } catch (e: SocketTimeoutException) {
        Log.d(TAG, "Timed out while accepting.")
        onTimeout?.invoke()
      }
    }
    return this
  }

  fun connectOnPort(
    port: Int,
    host: String,
    retry: Boolean,
    onConnect: () -> Unit
  ): SocketConnection {
    return connectOnPort(port, host, retry, onConnect, null)
  }

  fun connectOnPort(
    port: Int,
    host: String,
    retry: Boolean,
    onConnect: () -> Unit,
    onFail: ((String) -> Unit)?
  ): SocketConnection {
    thread {
      socket = Socket()
      try {
        // Create a SocketAddress with the host and port
        val socketAddress: SocketAddress = InetSocketAddress(host, port)

        // Set the connection timeout
        socket.connect(socketAddress, 4000)

        Log.d(TAG, "acceptOnPort() Connection was established.")
        onConnected(onConnect)
      } catch (e: SocketTimeoutException) {
        Log.d(TAG, "Socket Timeout Occurred")
        if (retry)
          connectOnPort(port, host, false, onConnect)
        else onFail?.invoke("Failed to reach the device.")
      } catch (e: Exception) {
        e.printStackTrace()
        if (retry)
          connectOnPort(port, host, false, onConnect)
        else onFail?.invoke("I/O Exception ${e.message}")
        Log.d(TAG, "I/O Exception ${e.message}")
      }
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

      sendBufferSize = Info.BUFFER_SIZE
      receiveBufferSize = Info.BUFFER_SIZE
    }
    onFinish.invoke()
  }

  fun close() {
    socket.close()
  }
}