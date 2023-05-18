package com.baxolino.apps.floats

import android.annotation.SuppressLint

@SuppressLint("UnsafeOptInUsageError")
class NsdFloats(private val home: HomeActivity, name: String): NsdInterface(home) {

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
    initializeServerSocket()
  }

  override fun accepted() {
    home.deviceConnected(true, null)
  }

  override fun connected(serviceName: String) {
    home.deviceConnected(false, serviceName)
  }
}