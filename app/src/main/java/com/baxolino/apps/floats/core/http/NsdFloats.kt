package com.baxolino.apps.floats.core.http

import android.annotation.SuppressLint
import com.baxolino.apps.floats.HomeActivity
import java.net.InetAddress

@SuppressLint("UnsafeOptInUsageError")
class NsdFloats(private val home: HomeActivity, name: String): NsdInterface(home) {

  // the device address of what we are connected to
  var hostAddress = ""

  companion object {
    private var nsdFloatsInstance: NsdFloats? = null

    fun getInstance(activity: HomeActivity, name: String): NsdFloats? {
      if (nsdFloatsInstance == null) {
        nsdFloatsInstance = NsdFloats(activity, name)
      }
      return nsdFloatsInstance
    }
  }

  init {
    registerService(name)
  }

  override fun accepted() {
    home.deviceConnected(true, null)
  }

  override fun connected(serviceName: String, host: InetAddress) {
    saveConnectedDevice(serviceName)

    hostAddress = host.hostAddress as String
    home.deviceConnected(false, serviceName)
  }
}