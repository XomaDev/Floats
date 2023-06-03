package com.baxolino.apps.floats.core.io

class Bytes(private val bytes: ByteArray) {
  private var index = 0
  private val len = bytes.size

  fun available(): Int {
    return len - index
  }

  val isEmpty: Boolean
    get() = available() == 0

  fun read(): Int {
    return if (available() == 0) -1 else bytes[index++].toInt()
  }
}