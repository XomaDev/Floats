package com.baxolino.apps.floats.core.bytes.io;

import java.io.IOException;
import java.io.InputStream;

public abstract class BitInputStream extends InputStream {

  private final InputStream stream;

  private int currentInt = 0;
  private int bitCursor = -1;

  public BitInputStream(InputStream stream) {
    this.stream = stream;
  }

  public int available() {
    if (stream != null) {
      int available;
      try {
        available = stream.available();
      } catch (IOException e) {
        throw new RuntimeException(e);
        // unreachable
      }
      if (bitCursor != -1)
        available++;
      return available;
    }
    // the extending class should override it
    return availableStream();
  }

  public abstract int availableStream();

  public abstract int readStream();

  int _read() {
    if (stream != null) {
      try {
        return stream.read();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    // the extending class should override it
    return readStream();
  }

  public int readShort16() {
    return ((byte) read() & 255) << 8 |
            (byte) read() & 255;
  }

  /**
   * Reads a 32-bit integer from the stream
   */

  public int readInt32() {
    return (((byte) read() & 255) << 24) |
            (((byte) read() & 255) << 16) |
            (((byte) read() & 255) << 8) |
            (((byte) read() & 255));
  }

  /**
   * Reads the next bit from the stream
   */
  public int readBit() {
    if (bitCursor == -1) { // no buffer
      currentInt = _read();
      if (currentInt == -1)
        return -1;
      bitCursor = 7;
    }
    // shift the number right n times
    // then find the LSB of it
    return (currentInt >> bitCursor--) & 1;
  }

  /**
   * Reads the next 8 bits to form a byte
   * @return a byte
   */


  public int read()  {
    int n = readBit();
    if (n == -1) // important
      return n;
    for (int i = 0; i < 7; i++) {
      int bit = readBit();
      if (bit == -1)
        break;
      n = (n << 1) | bit;
    }
    return n;
  }

  public int read(byte[] bytes) {
    int n = 0;
    for (int len = bytes.length; n < len; n++) {
      int read = read();
      if (read == -1)
        return n;
      bytes[n] = (byte) read;
    }
    return n;
  }

  public long skip(long n) {
    while (n-- > 0) //noinspection ResultOfMethodCallIgnored
      read();
    return -1;
  }
}
