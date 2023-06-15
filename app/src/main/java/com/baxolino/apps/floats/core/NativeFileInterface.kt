package com.baxolino.apps.floats.core

object NativeFileInterface {
  
  interface Callback {
    fun onStart()
    fun update(received: Int)
    fun cancelled()
  }
  
  external fun receiveFile(
    callback: Callback,
    outputDir: String,
    fileName: String,
    expectedSize: Int,
    host: String,
    port: Int
  ): String?

  external fun cancelFileReceive()
}