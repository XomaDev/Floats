package com.baxolino.apps.floats.core.io;

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

  private final LinkedList<Bytes> series = new LinkedList<>();

  private Bytes bytes = null;

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
    series.add(new Bytes(bytes));
    reachedEOS = false;
  }

  // removes the current chunk, this is mainly
  // because some data can have blank spots, i.e null bytes
  // because of strict chunk size system

  public void flushCurrent() {
    bytes = series.poll();
    if (bytes == null) {
      reachedEOS = true;
    }
  }

  @Override
  public int availableStream() {
    return available;
  }

  @Override
  public int readStream() {
    if (bytes == null || bytes.available() == 0) {
      bytes = series.poll();
      if (bytes == null) {
        reachedEOS = true;
        return -1;
      }
    }
    available--;
    return bytes.read();
  }

  public boolean reachedEOS() {
    return reachedEOS;
  }
}
