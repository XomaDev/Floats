package com.baxolino.apps.floats.core

class NativeSocketClient {
  
  interface Callback {
    fun onStart()
    fun update(received: Int)
  }
  
  external fun connectToHost(
    callback: Callback,
    output: String?,
    host: String?,
    port: Int
  ): String?
}