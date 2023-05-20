package com.baxolino.apps.floats.core.bytes.io;

import java.util.LinkedList;

public class DataInputStream extends BitInputStream {

  public DataInputStream() {
    super(null);
  }



  /**
   * Called when there is a new byte available;
   */

  public interface ByteListener {
    boolean onNewByteAvailable(byte b) ;
  }

  private ByteListener byteListener = null;

  private final LinkedList<Chunk> chunks = new LinkedList<>();

  private Chunk chunk = null;

  private boolean reachedEOS = true;

  private int available = 0;


  public void setByteListener(ByteListener listener) {
    byteListener = listener;
  }

  public ByteListener getByteListener() {
    return byteListener;
  }

  public void addChunk(byte[] bytes) {
    available += bytes.length;
    chunks.add(new Chunk(bytes));
    reachedEOS = false;
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
