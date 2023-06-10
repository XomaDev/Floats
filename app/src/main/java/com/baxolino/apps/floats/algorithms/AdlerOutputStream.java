package com.baxolino.apps.floats.algorithms;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Adler32;

public class AdlerOutputStream extends OutputStream {

  private final Adler32 adler32 = new Adler32();


  private final OutputStream output;

  public AdlerOutputStream(OutputStream output) {
    this.output = output;
  }

  public long getValue() {
    return adler32.getValue();
  }

  @Override
  public void write(int b) throws IOException {
    output.write(b);
    adler32.update(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    output.write(b);
    adler32.update(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    output.write(b, off, len);
    adler32.update(b, off, len);
  }

  @Override
  public void close() throws IOException {
    output.close();
  }
}
