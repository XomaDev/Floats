package com.baxolino.apps.floats.algorithms

import java.io.OutputStream
import java.util.zip.Adler32

class AdlerOutputStream(private val output: OutputStream) : OutputStream() {

  private val adler32 = Adler32()

  val value: Long
    get() = adler32.value

  override fun write(b: Int) {
    output.write(b)
    adler32.update(b)
  }

  override fun write(b: ByteArray) {
    output.write(b)
    adler32.update(b)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    output.write(b, off, len)
    adler32.update(b, off, len)
  }

  override fun close() {
    output.close()
  }
}