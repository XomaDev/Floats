package com.baxolino.apps.floats.nsd

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.baxolino.apps.floats.core.transfer.SocketUtils
import com.baxolino.apps.floats.tools.Utils
import java.io.IOException

class NsdInterface constructor(val context: Context) {

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

  init {
    discover()


    // this should process any additional packets
    // if any
    multicastLock.setReferenceCounted(true)
    multicastLock.acquire()
  }

  // when we find a device, we will save it here, and also
  // remove it when we are unable to find it anymore

  interface ServiceAvailableListener {
    fun available(name: String, port: Int, host: String)
    fun disappeared(name: String)
  }

  private var serviceListener: ServiceAvailableListener? = null

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

  fun registerAvailabilityListener(listener: ServiceAvailableListener) {
    serviceListener = listener
  }

  private fun detach() {
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
        Log.d(TAG, "###### Connection Was Accepted")

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

  fun discover() {
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
          resolve(service)
        }
      }

      override fun onServiceLost(service: NsdServiceInfo) {
        // When the network service is no longer available.
        // Internal bookkeeping code goes here.
        Log.e(TAG, "service lost: $service")
        val serviceName = service.serviceName

        if (serviceName.startsWith(NAME_PREFIX)) {
          val device = serviceName.substring(NAME_PREFIX.length)

          serviceListener?.disappeared(device)
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

    nsdManager.discoverServices(
      SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener
    )
  }

  private fun resolve(serviceInfo: NsdServiceInfo) {
    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
      override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
        Log.d(TAG, "Resolve failed: $errorCode")
      }

      @SuppressLint("UnsafeOptInUsageError")
      override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
        mService = nsdServiceInfo

        val port = mService.port
        val host = mService.host

        val hostAddress = host.hostAddress!!

        val ourIpv4 = Utils.getIpv4(context)
        if (ourIpv4 != null && hostAddress == ourIpv4.hostAddress)
          return

        Log.e(TAG, "Resolve Succeeded. $mService port $port $host")
        serviceListener?.available(
          serviceInfo.serviceName.substring(NAME_PREFIX.length),
          port,
          hostAddress
        )
      }
    })
  }
}