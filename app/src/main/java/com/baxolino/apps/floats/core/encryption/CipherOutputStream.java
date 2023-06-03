package com.baxolino.apps.floats.core.encryption;

import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

public class CipherOutputStream extends OutputStream {

  private final OutputStream output;
  private final Cipher cipher;

  public CipherOutputStream(OutputStream output, Cipher cipher) {
    this.output = output;
    this.cipher = cipher;
  }

  @Override
  public void write(byte[] input, int off, int len) throws IOException {
    byte[] finalOutput = finalize(input, off, len);
    // structure:
    //      byte streams[coded length | encrypted content]
    int lenOutput = finalOutput.length;

    output.write(lenOutput >> 24);
    output.write(lenOutput >> 16);
    output.write(lenOutput >> 8);
    output.write(lenOutput);

    output.write(finalize(input, off, len));
  }


  @Override
  public void write(int b) throws IOException {
    write(new byte[] {(byte) b}, 0, 1);
  }

  private byte[] finalize(byte[] input, int off, int len) {
    try {
      return cipher.doFinal(input, off, len);
    } catch (BadPaddingException | IllegalBlockSizeException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void flush() throws IOException {
    output.flush();
  }

  @Override
  public void close() throws IOException {
    flush();
    output.close();
  }
}
