package com.baxolino.apps.floats.core

class NativeInterface {
  
  interface Callback {
    fun onStart()
    fun update(received: Int)
    fun cancelled()
  }
  
  external fun receiveFile(
    callback: Callback,
    output: String?,
    expectedSize: Int,
    host: String?,
    port: Int
  ): String?

  external fun cancelFileReceive()
}