package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.baxolino.apps.floats.core.http.SocketUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

abstract class NsdInterface constructor(context: Context) {

  companion object {
    private const val NAME_PREFIX = "Floats"
    private const val SERVICE_TYPE = "_floats._tcp"

    private const val TAG = "NsdInterface"
  }

  enum class State {
    NORMAL, CANCELLED
  }


  var state = State.NORMAL

  private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)

  private val localPort: Int = SocketUtils.findAvailableTcpPort()

  private var discoveryListener: NsdManager.DiscoveryListener? = null
  private lateinit var mService: NsdServiceInfo

  @JvmField
  var input: InputStream? = null

  @JvmField
  var output: OutputStream? = null

  private val registrationListener: NsdManager.RegistrationListener = object :
    NsdManager.RegistrationListener {
    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
      // Save the service name. Android may have changed it in order to
      // resolve a conflict, so update the name you initially requested
      // with the name Android actually used.
      Log.d(TAG, "onServiceRegistered Service Name = " + serviceInfo.serviceName)
    }

    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
      // Registration failed! Put debugging code here to determine why.
      Log.d(TAG, "onRegistrationFailed: $errorCode")
    }

    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
      // Service has been unregistered. This only happens when you call
      // NsdManager.unregisterService() and pass in this listener.
      Log.d(TAG, "onServiceUnregistered")
    }

    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
      // Unregistration failed. Put debugging code here to determine why.
      Log.d(TAG, "onUnregistrationFailed: $errorCode")
    }
  }

  fun unregister() {
    nsdManager.unregisterService(registrationListener)
  }

  fun detach() {
    discoveryListener?.let {
      nsdManager.stopServiceDiscovery(it)
    }
  }

  private fun initializeServerSocket() {
    // this basically allows any incoming request
    // to connect to the server

    Thread {
      try {
        val serverSocket = ServerSocket(localPort)
        val socket = serverSocket.accept()
        socket.keepAlive = true

        input = socket.getInputStream()
        output = socket.getOutputStream()

        Log.d(TAG, "###### Connection Was Accepted")

        accepted()
      } catch (e: IOException) {
        Log.d(TAG, "initializeServerSocket: Failed to Accept")
        throw RuntimeException(e)
      }
    }.start()
  }

  fun registerService(name: String) {
    val serviceInfo = NsdServiceInfo()
      .apply {
        serviceName = NAME_PREFIX + name
        serviceType = "_" + NAME_PREFIX.lowercase() + "._tcp"
        port = localPort
      }

    nsdManager.registerService(
      serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener
    )
    initializeServerSocket()
  }

  /**
   * Starts service discovery and connects to %requestName%
   * if found
   *
   * @param requestName the device to connect to
   */

  fun discover(requestName: String) {
    discoveryListener = object : NsdManager.DiscoveryListener {
      // Called as soon as service discovery begins.
      override fun onDiscoveryStarted(regType: String) {
        Log.d(TAG, "Service discovery started")
      }

      override fun onServiceFound(service: NsdServiceInfo) {
        // A service was found! Do something with it.
        Log.d(TAG, "Service discovery success$service")
        val serviceName = service.serviceName

        if (serviceName.startsWith(NAME_PREFIX)) {
          val device = serviceName.substring(NAME_PREFIX.length)

          Log.d(TAG, "Found Service = " + device + " port " + service.port)
          if (device == requestName) {
            Log.d(TAG, "Requesting Connection")
            requestConnection(requestName, service)
          }
        }
      }

      override fun onServiceLost(service: NsdServiceInfo) {
        // When the network service is no longer available.
        // Internal bookkeeping code goes here.
        Log.e(TAG, "service lost: $service")
      }

      override fun onDiscoveryStopped(serviceType: String) {
        Log.i(TAG, "Discovery stopped: $serviceType")
      }

      override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        nsdManager.stopServiceDiscovery(this)
      }

      override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        nsdManager.stopServiceDiscovery(this)
      }
    }
    nsdManager.discoverServices(
      SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener
    )
  }

  private fun requestConnection(requestName: String, serviceInfo: NsdServiceInfo) {
    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
      override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
        Log.d(TAG, "Resolve failed: $errorCode")
      }

      @SuppressLint("UnsafeOptInUsageError")
      override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
        mService = nsdServiceInfo

        val port = mService.port
        val host = mService.host

        Log.d(TAG, "onServiceResolved: host = $host")

        try {
          Log.d(TAG, "Connecting $host on $port")
          val socket = Socket(host, port)
          socket.keepAlive = true


          input = socket.getInputStream()
          output = socket.getOutputStream()

          Log.d(TAG, "######## Connection Was Established")

          connected(requestName)
          Log.e(TAG, "Resolve Succeeded. $mService port $port")
        } catch (io: IOException) {
          Log.d(TAG, "Failed To Connect")
        }
      }
    })
  }

  abstract fun accepted()

  abstract fun connected(serviceName: String)
}