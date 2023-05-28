package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.baxolino.apps.floats.core.Config
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


  private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
  private val multicastLock: WifiManager.MulticastLock = (
          context.getSystemService(WifiManager::class.java)).createMulticastLock(TAG)

  private val localPort: Int = SocketUtils.findAvailableTcpPort()

  private var discoveryListener: NsdManager.DiscoveryListener? = null

  private lateinit var mService: NsdServiceInfo
  private var mPreferences: SharedPreferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

  // when we find a device, we will save it here, and also
  // remove it when we are unable to find it anymore

  interface ServiceAvailableListener {
    fun available()
    fun disappeared()
  }

  private val serviceListeners = HashMap<String, ServiceAvailableListener>()

  lateinit var input: InputStream

  lateinit var output: OutputStream

  lateinit var socket: Socket

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

  fun registerAvailabilityListener(name: String, listener: ServiceAvailableListener) {
    serviceListeners[name] = listener
  }

  fun unregister() {
    nsdManager.unregisterService(registrationListener)
  }

  fun detach() {
    discoveryListener?.let {
      nsdManager.stopServiceDiscovery(it)
    }
    if (multicastLock.isHeld)
      multicastLock.release()
  }

  private fun initializeServerSocket() {
    // this basically allows any incoming request
    // to connect to the server

    Thread {
      try {
        val serverSocket = ServerSocket(localPort)
        val socket = serverSocket.accept()

        socket.keepAlive = true
        this.socket = socket

        setSocketProps()

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

  init {
    discover("")
  }

  /**
   * Starts service discovery and connects to %requestName%
   * if found
   *
   * @param requestName the device to connect to
   */

  fun discover(requestName: String) {
    // stop old discovery, (if any)
    detach()

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

          Log.d(TAG, "Found Service = " + device + " port " + service.port + " needs $requestName")

          val listener = serviceListeners[device]
          Log.d(TAG, "onServiceFound: $listener")
          listener?.apply { available() }

          if (device == requestName) {
            Log.d(TAG, "Requesting Connection")
            requestConnection(requestName, service)
            return
          }
        }
      }

      override fun onServiceLost(service: NsdServiceInfo) {
        // When the network service is no longer available.
        // Internal bookkeeping code goes here.
        Log.e(TAG, "service lost: $service")
        val serviceName = service.serviceName

        if (serviceName.startsWith(NAME_PREFIX)) {
          val device = serviceName.substring(NAME_PREFIX.length)

          val listener = serviceListeners[device]
          listener?.apply { disappeared() }
        }
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

    // this should process any additional packets
    // if any
    multicastLock.setReferenceCounted(true)
    multicastLock.acquire()

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

          this@NsdInterface.socket = socket
          setSocketProps()

          Log.d(TAG, "######## Connection Was Established")

          connected(requestName)

          Log.e(TAG, "Resolve Succeeded. $mService port $port")
        } catch (io: IOException) {
          Log.d(TAG, "Failed To Connect")
        }
      }
    })
  }

  private fun setSocketProps() {
    // this will enable reading of urgent data using
    // regular input streams
    if (!socket.oobInline)
      socket.oobInline = true

    socket.receiveBufferSize = Config.BUFFER_SIZE
    socket.sendBufferSize = Config.BUFFER_SIZE

    input = socket.getInputStream()
    output = socket.getOutputStream()
  }

  // will also be called from outside the class
  fun saveConnectedDevice(name: String) {
    mPreferences.edit().putString("device", name)
      .apply()
  }

  fun retrieveSavedDevice() : String {
    return mPreferences.getString("device", "")!!
  }

  abstract fun accepted()

  abstract fun connected(serviceName: String)
}