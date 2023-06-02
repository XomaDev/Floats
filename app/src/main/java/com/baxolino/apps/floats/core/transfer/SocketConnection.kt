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
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
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

  lateinit var secureInput: InputStream
  lateinit var secureOutput: OutputStream


  private val keys: KeyPair = AsymmetricEncryption.getKeyPair()

  private lateinit var encryptCipher: Cipher
  private lateinit var decryptCipher: Cipher

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

    exchangeKeys(onFinish)
  }

  /**
   * RSA (public keys) are exchanged with each
   * other, (max delay = 50ms)
   */
  private fun exchangeKeys(onFinish: () -> Unit) {
    // send the RSA public key to the other device
    val public = keys.public
    Log.d(TAG, "Sending Public Key")
    Log.d(TAG, "Sending Public Key ${keys.public.encoded.contentToString()}")

    public.encoded.apply {
      // send the key size
      output.let {
        it.write(size shr 8 and 0xFF)
        it.write(size and 0xFF)

        it.write(this)
      }
    }
    decryptCipher = AsymmetricEncryption.init(Cipher.DECRYPT_MODE, keys.private)

    // receiving their public key
    val executor = ScheduledThreadPoolExecutor(1)
    executor.schedule({
      Log.d(TAG, "exchangeKeys: ---------------------- " + input.available())
      val publicKeyBytesLen =
        (input.read() and 255 shl 8) or (input.read() and 255)

      Log.d(TAG, "exchangeKeys: length = $publicKeyBytesLen")
      val encryptionKeyBytes = ByteArray(publicKeyBytesLen)

      var offset = 0
      while (offset != publicKeyBytesLen)
        offset += input.read(encryptionKeyBytes, offset, publicKeyBytesLen - offset)
      Log.d(
        TAG,
        "exchangeKeys: Received $offset / $publicKeyBytesLen hash = ${
          encryptionKeyBytes.contentToString()
        }"
      )

      val encryptionKey = AsymmetricEncryption.getPublicKey(encryptionKeyBytes)
      encryptCipher = AsymmetricEncryption.init(Cipher.ENCRYPT_MODE, encryptionKey)

      Log.d(TAG, "Exchanged Public Key")
      initSecureStreams()
      onFinish.invoke()

      executor.shutdownNow()
    }, 50, TimeUnit.MILLISECONDS)
  }

  private fun initSecureStreams() {
    input = CipherInputStream(input, decryptCipher)
    output = CipherOutputStream(output, encryptCipher)
  }

  fun close() {
    socket.close()
  }
}