package com.baxolino.apps.floats.core.io

class Bytes(private val bytes: ByteArray) {
  private var index = 0
  private val len = bytes.size

  fun available() = len - index

  val isEmpty: Boolean
    get() = available() == 0

  fun read() = if (available() == 0) -1 else bytes[index++].toInt()
}