package com.baxolino.apps.floats.core.bytes.io;

import java.io.IOException;
import java.util.LinkedList;

public class BitInputStream {

  private final LinkedList<Chunk> chunks = new LinkedList<>();

  private Chunk chunk = null;

  private boolean reachedEOS = false;

  private int currentInt = 0;
  private int bitCursor = -1;


  public void addChunk(byte[] bytes) {
    chunks.add(new Chunk(bytes));
  }

  private int readChunkInt() {
    if (chunk == null || chunk.available() == 0) {
      chunk = chunks.poll();
      if (chunk == null) {
        reachedEOS = true;
        return -1;
      }
    }
    return chunk.read();
  }

  public boolean reachedEOS() {
    return reachedEOS;
  }

  public int readShort16() throws IOException {
    return ((byte) read() & 255) << 8 |
            (byte) read() & 255;
  }

  /**
   * Reads a 32-bit integer from the stream
   */

  public int readInt32() throws IOException {
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
      currentInt = readChunkInt();
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

  public int read() {
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
}
