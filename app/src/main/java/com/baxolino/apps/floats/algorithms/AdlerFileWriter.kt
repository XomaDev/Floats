package com.baxolino.apps.floats.algorithms

import android.util.Log
import com.baxolino.apps.floats.core.Info
import com.baxolino.apps.floats.core.io.BitStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Arrays
import java.util.zip.GZIPOutputStream

class AdlerFileWriter(private val input: InputStream, private val output: OutputStream) {

  fun write(listener: (Int) -> Unit) {
    val adlerOutputStream = AdlerOutputStream(output)

    val zipOutput = GZIPOutputStream(adlerOutputStream)
    val buffer = ByteArray(Info.BUFFER_SIZE)

    var read: Int
    var written = 0
    while (input.read(buffer).also { read = it } > 0) {
      zipOutput.write(buffer, 0, read)

      written += read
      listener.invoke(written)
    }
    zipOutput.finish()

    // send the checksum over to the other
    // device for verification; after sending bytes
    val adlerCheckSum = adlerOutputStream.value

    val checksum = BitStream()
      .writeLong64(adlerCheckSum)
      .toBytes()

    Log.d(TAG, "CheckSum = " + adlerCheckSum + " | " + Arrays.toString(checksum))

    output.write(
      BitStream()
        .writeLong64(adlerCheckSum)
        .toBytes()
    )
    output.close()
    input.close()
  }

  companion object {
    private const val TAG = "AdlerFileWriter"
  }
}