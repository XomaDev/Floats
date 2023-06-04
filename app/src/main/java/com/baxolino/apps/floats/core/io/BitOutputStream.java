package com.baxolino.apps.floats.core.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

public class BitOutputStream {

  private final OutputStream output;

  private int currentByte;
  public int bitsWritten = 0;

  public BitOutputStream(OutputStream output) {
    this.output = output;
  }

  /**
   * Appends a bit to the current byte, writes the byte to stream
   * and resets it once reached len 8
   */

  public BitOutputStream writeBit(int b) throws IOException {
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
   * Writes the full byte array to the stream
   *
   * @param b bytes to be written
   */
  public BitOutputStream write(byte[] b) throws IOException {
    return write(b, 0, b.length);
  }

  /**
   * Writes the byte array starting from offset
   *
   * @param b   bytes to be written
   * @param off starting point
   * @param len length of bytes to be written
   */
  public BitOutputStream write(byte[] b, int off, int len) throws IOException {
    for (int i = 0; i < len; i++)
      write(b[off + i]);
    return this;
  }

  /**
   * Converts the @param b to bits and writes to the stream
   *
   * @param n byte to be written
   */

  public BitOutputStream write(int n) throws IOException {
    n &= 0xff; // un-sign it

    // range: 0 ~ 256 only
    // convert the ints to bits
    for (int i = 7; i >= 0; i--)
      writeBit((n >> i) & 1);
    return this;
  }

  /**
   * Writes 32-bit integer to output
   */
  public BitOutputStream writeInt32(int n) throws IOException {
    write(n >> 24);
    write(n >> 16);
    write(n >> 8);
    write(n);

    return this;
  }


  /**
   * Writes a 16-bit short int to stream
   */
  public BitOutputStream writeShort16(short n) throws IOException {
    write(n >> 8);
    write((byte) n);

    return this;
  }

  public void flush() throws IOException {
    if (bitsWritten != 0)
      output.write(currentByte);
  }
}
