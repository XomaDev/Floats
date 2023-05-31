package com.baxolino.apps.floats.core.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.util.Log
import com.baxolino.apps.floats.core.Config
import com.baxolino.apps.floats.core.encryption.AsymmetricEncryption
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import kotlin.concurrent.thread

class SocketConnection (private val localPort: Int) {

  companion object {
    private const val TAG = "SocketConnection"

    fun getIpv4(context: Context): InetAddress {
      val connector = context.getSystemService(ConnectivityManager::class.java)

      // this may return null when not connected to any network
      val linkProperties = connector.getLinkProperties(connector.activeNetwork) as LinkProperties
      for (linkAddress in linkProperties.linkAddresses) {
        val address = linkAddress.address
        val hostAddress = address.hostAddress

        // we have to look for Ip4 address here
        if (isValidIpv4(hostAddress))
          return InetAddress.getByAddress(address.address)
      }
      throw Error("Could not find Ipv4 address")
    }

    private fun isValidIpv4(ip: String?): Boolean {
      return try {
        if (ip.isNullOrEmpty()) return false
        val parts = ip.split("\\.".toRegex())
          .dropLastWhile { it.isEmpty() }
          .toTypedArray()
        if (parts.size != 4) return false
        for (s in parts) {
          val i = s.toInt()
          if (i < 0 || i > 255) {
            return false
          }
        }
        !ip.endsWith(".")
      } catch (nfe: NumberFormatException) {
        false
      }
    }

    private var connectionMain: SocketConnection? = null

    fun getMainInstance(localPort: Int): SocketConnection {
      connectionMain?.let {
        throw Error("Socket Connection already exists")
      }
      SocketConnection(localPort)
        .apply {
          connectionMain = this
          return this
        }
    }

    fun getMainExisting(): SocketConnection {
      connectionMain?.let { return it }
      throw Error("Socket Connection Not Initialized")
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

      onConnected()
      onConnect.invoke()
    }
    return this
  }

  fun connectOnPort(port: Int, host: String, onConnect: () -> Unit): SocketConnection {
    thread {
      socket = Socket(host, port)
      Log.d(TAG, "acceptOnPort() Connection was established.")

      onConnected()
      onConnect.invoke()
    }
    return this
  }

  private fun onConnected() {
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
  }

  fun close() {
    socket.close()
  }
}