package com.baxolino.apps.floats.core.bytes.io;

import java.util.LinkedList;

public class DataInputStream extends BitInputStream {

  public DataInputStream() {
    super(null);
  }

  public interface ChunkListener {
    void onNewChunksAvailable();
  }


  /**
   * Called when there is a new byte available;
   * unsigned byte is the unsigned form of the byte
   * eval (b & 0xff) == unsigned
   */

  public interface ByteListener {
    boolean onNewByteAvailable(int byteIndex, byte b, int unsigned);
  }

  private ChunkListener listener;

  private ByteListener byteListener = null;

  private final LinkedList<Chunk> chunks = new LinkedList<>();

  private Chunk chunk = null;

  private boolean reachedEOS = true;

  private int available = 0;


  public void setChunkListener(ChunkListener listener) {
    this.listener = listener;
    if (listener != null && !reachedEOS)
      listener.onNewChunksAvailable();
  }

  public DataInputStream setByteListener(ByteListener listener) {
    byteListener = listener;
    return this;
  }

  public ByteListener getByteListener() {
    return byteListener;
  }

  public void addChunk(byte[] bytes) {
    available += bytes.length;
    chunks.add(new Chunk(bytes));
    reachedEOS = false;

    if (listener != null)
      listener.onNewChunksAvailable();
  }

  // removes the current chunk, this is mainly
  // because some data can have blank spots, i.e null bytes
  // because of strict chunk size system

  public void flushCurrent() {
    chunk = chunks.poll();
    if (chunk == null) {
      reachedEOS = true;
    }
  }

  @Override
  public int availableStream() {
    return available;
  }

  @Override
  public int readStream() {
    if (chunk == null || chunk.available() == 0) {
      chunk = chunks.poll();
      if (chunk == null) {
        reachedEOS = true;
        return -1;
      }
    }
    available--;
    return chunk.read();
  }

  public boolean reachedEOS() {
    return reachedEOS;
  }
}
