package com.baxolino.apps.floats.core.bytes.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

public class BitInputStream extends InputStream {

  public interface ChunkListener {
    void onNewChunksAvailable();
  }

  private ChunkListener listener;

  private final LinkedList<Chunk> chunks = new LinkedList<>();

  private Chunk chunk = null;

  private boolean reachedEOS = true;

  private int currentInt = 0;
  private int bitCursor = -1;


  public void setChunkListener(ChunkListener listener) {
    this.listener = listener;
    if (listener != null && !reachedEOS)
      listener.onNewChunksAvailable();
  }

  public void addChunk(byte[] bytes) {
    chunks.add(new Chunk(bytes));
    reachedEOS = false;

    if (listener != null)
      listener.onNewChunksAvailable();
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

  public int readShort16()  {
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
}
