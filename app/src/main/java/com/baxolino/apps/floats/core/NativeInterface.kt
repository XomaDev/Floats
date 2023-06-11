package com.baxolino.apps.floats.core

class NativeInterface {
  
  interface Callback {
    fun onStart()
    fun update(received: Int)
    fun cancelled()

    fun debug(s: Int)
  }
  
  external fun connectToHost(
    callback: Callback,
    output: String?,
    host: String?,
    port: Int
  ): String?

  external fun cancel()
}