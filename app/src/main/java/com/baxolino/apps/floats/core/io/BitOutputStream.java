package com.baxolino.apps.floats.core.io;

import java.io.ByteArrayOutputStream;

public class BitOutputStream {

  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  private int currentByte;
  public int bitsWritten = 0;

  /**
   * Appends a bit to the current byte, writes the byte to stream
   * and resets it once reached len 8
   */

  public BitOutputStream writeBit(int b) {
    currentByte = (currentByte << 1) | b;
    bitsWritten++;
    if (bitsWritten == 8) {
      output.write(currentByte);

      bitsWritten = 0;
      currentByte = 0;
    }

    return this;
  }

  /**
   * Converts the @param b to bits and writes to the stream
   *
   * @param n byte to be written
   */

  public BitOutputStream write(int n) {
    n &= 0xff; // un-sign it

    // range: 0 ~ 256 only
    // convert the ints to bits
    for (int i = 7; i >= 0; i--)
      writeBit((n >> i) & 1);
    return this;
  }

  public BitOutputStream write(byte[] bytes) {
    for (byte b : bytes)
      write(b);
    return this;
  }

  /**
   * Writes 32-bit integer to output
   */
  public BitOutputStream writeInt32(int n) {
    write(n >> 24);
    write(n >> 16);
    write(n >> 8);
    write(n);

    return this;
  }


  /**
   * Writes a 16-bit short int to stream
   */
  public BitOutputStream writeShort16(short n) {
    write(n >> 8);
    write((byte) n);

    return this;
  }

  public void close() {
    if (bitsWritten != 0)
      output.write(currentByte);
  }

  public byte[] toBytes() {
    close();
    return output.toByteArray();
  }
}