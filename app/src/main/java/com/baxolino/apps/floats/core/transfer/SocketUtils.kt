package com.baxolino.apps.floats.core.transfer

import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket
import java.util.Random

object SocketUtils {
  /**
   * The default minimum value for port ranges used when finding an available
   * socket port.
   */
  private const val PORT_RANGE_MIN = 1024

  /**
   * The default maximum value for port ranges used when finding an available
   * socket port.
   */
  private const val PORT_RANGE_MAX = 65535

  private const val PORT_RANGE = PORT_RANGE_MAX - PORT_RANGE_MIN

  private val random = Random(System.currentTimeMillis())

  /**
   * Determine if the specified port for this `SocketType` is
   * currently available on `localhost`.
   */
  private fun isPortAvailable(port: Int): Boolean {
    var sSocket: ServerSocket? = null
    var dSocket: DatagramSocket? = null

    try {
      sSocket = ServerSocket(port)
      sSocket.reuseAddress = true

      dSocket = DatagramSocket(port)
      dSocket.reuseAddress = true

      return true
    } catch (ignored: IOException) {

    } finally {
      dSocket?.close()
      sSocket?.let {
        try {
          it.close()
        } catch (ignored: IOException) {
          // this should not be thrown
        }
      }
    }
    return false
  }

  /**
   * Find a pseudo-random port number
   */
  private fun findRandomPort(): Int {
    val portRange = PORT_RANGE_MAX - PORT_RANGE_MIN
    return PORT_RANGE_MIN + random.nextInt(portRange + 1)
  }

  /**
   * Finds available TCP port
   */
  fun findAvailableTcpPort(): Int {
    var candidatePort: Int
    var searchCounter = 0
    do {
      check(searchCounter <= PORT_RANGE) {
        String.format(
          "Could not find an available TCP port in the range [%d, %d] after %d attempts",
          PORT_RANGE_MIN, PORT_RANGE_MAX, searchCounter
        )
      }
      candidatePort = findRandomPort()
      searchCounter++
    } while (!isPortAvailable(candidatePort))
    return candidatePort
  }
}