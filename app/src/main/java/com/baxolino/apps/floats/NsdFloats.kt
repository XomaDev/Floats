package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.baxolino.apps.floats.core.http.SocketUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class NsdFloats private constructor(private val activity: HomeActivity, name: String) {

  private val nsdManager: NsdManager
  private val port: Int = SocketUtils.findAvailableTcpPort()

  private var mService: NsdServiceInfo? = null

  @JvmField
  var input: InputStream? = null

  @JvmField
  var output: OutputStream? = null

  @SuppressLint("UnsafeOptInUsageError")
  fun initializeServerSocket() {
    // this basically allows any incoming request
    // to connect to the server
    Thread {
      try {
        val serverSocket = ServerSocket(port)
        val socket = serverSocket.accept()

        input = socket.getInputStream()
        output = socket.getOutputStream()

        Log.d(TAG, "###### Connection Was Accepted")

        activity.deviceConnected(true, null)
      } catch (e: IOException) {
        throw RuntimeException(e)
      }
    }.start()
  }

  private val registrationListener: RegistrationListener = object : RegistrationListener {
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

  /**
   * Starts service discovery and connects to %requestName%
   * if found
   *
   * @param requestName the device to connect to
   */
  fun discover(requestName: String) {
    // TODO:
    //  stop the previous discovery in the future
    discoveryListener = object : DiscoveryListener {
      // Called as soon as service discovery begins.
      override fun onDiscoveryStarted(regType: String) {
        Log.d(TAG, "Service discovery started")
      }

      override fun onServiceFound(service: NsdServiceInfo) {
        // A service was found! Do something with it.
        Log.d(TAG, "Service discovery success$service")
        val serviceName = service.serviceName

        if (serviceName.startsWith(NAME)) {
          val device = serviceName.substring(NAME.length)

          Log.d(TAG, "Found Device = " + device + " port " + service.port)
          if (device == requestName) {
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

        val port = mService!!.port
        val host = mService!!.host

        Log.d(TAG, "onServiceResolved: host = $host")

        val socket: Socket
        try {
          Log.d(TAG, "Connecting $host on $port")
          socket = Socket(host, port)


          input = socket.getInputStream()
          output = socket.getOutputStream()

          Log.d(TAG, "######## Connection Was Established")

          activity.deviceConnected(false, requestName)
          Log.e(TAG, "Resolve Succeeded. $mService port $port")
        } catch (io: IOException) {
          Log.d(TAG, "Failed To Connect")
        }
      }
    })
  }

  private var discoveryListener: DiscoveryListener? = null

  /**
   * Registers a service on constructor
   * creation
   */
  init {
    Log.d(TAG, "Registering on port $port")

    val serviceInfo = NsdServiceInfo()

    serviceInfo.serviceName = NAME + name
    serviceInfo.serviceType = SERVICE_TYPE
    serviceInfo.port = port

    nsdManager = activity.getSystemService(NsdManager::class.java)

    nsdManager.registerService(
      serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener
    )
    initializeServerSocket()
  }

  companion object {
    private const val TAG = "NsdFloats"
    private const val NAME = "Floats"
    private const val SERVICE_TYPE = "_floats._tcp"
    private var nsdFloatsInstance: NsdFloats? = null
    fun getInstance(activity: HomeActivity, name: String): NsdFloats? {
      if (nsdFloatsInstance == null) {
        nsdFloatsInstance = NsdFloats(activity, name)
      }
      return nsdFloatsInstance
    }
  }
}