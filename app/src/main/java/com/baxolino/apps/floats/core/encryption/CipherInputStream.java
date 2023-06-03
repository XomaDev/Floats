package com.baxolino.apps.floats.core.encryption;

import com.baxolino.apps.floats.core.io.Bytes;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

public class CipherInputStream extends InputStream {

  private final InputStream input;
  private final Cipher cipher;

  private final LinkedList<Bytes> series = new LinkedList<>();

  // the current block of bytes awaiting to be
  // read...
  private Bytes bytes = null;

  private AtomicInteger available = new AtomicInteger(0);

  public CipherInputStream(InputStream input, Cipher cipher) {
    this.input = input;
    this.cipher = cipher;

    new Thread(() -> {
      try {
        readIncomingChunks();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).start();
  }

  private void readIncomingChunks() throws IOException {
    // TODO:
    //  we need a mechanism to know when to stop
    //  this loop
    for (;;) {
      // block size: repr by 4 byte, 32 bit integer
      if (!(input.available() > 4))
        continue;

      int blockSize = (((byte) input.read() & 255) << 24) |
              (((byte) input.read() & 255) << 16) |
              (((byte) input.read() & 255) << 8) |
              (((byte) input.read() & 255));

      byte[] buffer = new byte[blockSize];
      int offset = 0;
      while (offset != blockSize)
        // TODO:
        //  in the future we may need a thing to interrupt
        //  this, to not keep going infinitely
        offset += input.read(buffer, offset, blockSize - offset);

      byte[] finalized = finalize(buffer);

      available.addAndGet(finalized.length);
      series.add(new Bytes(finalized));
    }
  }

  private byte[] finalize(byte[] buffer) {
    try {
      return cipher.doFinal(buffer);
    } catch (BadPaddingException | IllegalBlockSizeException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int read() throws IOException {
    if (bytes == null || bytes.isEmpty()) {
      bytes = series.poll();
      if (bytes == null)
        return -1;
    }
    available.decrementAndGet();
    return bytes.read();
  }

  @Override
  public int available() {
    return available.get();
  }
}
