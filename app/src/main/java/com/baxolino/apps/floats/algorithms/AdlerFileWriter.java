package com.baxolino.apps.floats.algorithms;

import android.util.Log;

import androidx.annotation.NonNull;

import com.baxolino.apps.floats.core.Info;
import com.baxolino.apps.floats.core.io.BitStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.GZIPOutputStream;

public class AdlerFileWriter {

  private static final String TAG = "AdlerFileWriter";

  private final InputStream input;

  private final OutputStream output;


  public AdlerFileWriter(InputStream input, OutputStream output) {
    this.input = input;
    this.output = output;
  }

  public void write() throws IOException {
    AdlerOutputStream adlerOutputStream = new AdlerOutputStream(output);
    GZIPOutputStream zipOutput = new GZIPOutputStream(adlerOutputStream);

    byte[] buffer = new byte[Info.BUFFER_SIZE];

    int read;
    while ((read = input.read(buffer)) > 0)
      zipOutput.write(buffer, 0, read);
    zipOutput.finish();

    long adlerCheckSum = adlerOutputStream.getValue();
    byte[] checksum = new BitStream()
            .writeLong64(adlerCheckSum)
            .toBytes();
    Log.d(TAG, "CheckSum = " + adlerCheckSum + " | " + Arrays.toString(checksum));

    output.write(
            new BitStream()
                    .writeLong64(adlerCheckSum)
                    .toBytes()
    );
    output.close();
    input.close();
  }
}
