package com.baxolino.apps.floats.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MultiChannelSystem {

  private final OutputStream stream;

  private final ArrayList<ByteChunk> byteChunks = new ArrayList<>();

  public MultiChannelSystem(OutputStream stream) {
    this.stream = stream;
  }

  public void add(Channel channel, byte[] bytes) {
    byteChunks.add(new ByteChunk(channel, bytes));
  }

  public void start() {
    for (;;) {
      if (write()) {
        // tries to write again
        if (write()) {
          // we will check if there is any other
          // pending data after 1 second
          ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
          service.schedule(this::start, 70, TimeUnit.MILLISECONDS);
          break;
        }
      }
    }
  }


  private boolean write() {
    if (byteChunks.isEmpty())
      return true;
    ByteChunk chunk = byteChunks.remove(0);
    try {
      stream.write(chunk.channel.bytes());
      int blankSpots = Config.CHUNK_SIZE - chunk.bytes.length;
      if (blankSpots < 0)
        throw new RuntimeException("Bytes are more than chunk limit.");

      stream.write(blankSpots >> 8);
      stream.write(blankSpots);

      stream.write(chunk.bytes);

      while (blankSpots-- > 0)
        stream.write(0);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return false;
  }

  static class ByteChunk {

    private final Channel channel;
    private final byte[] bytes;

    public ByteChunk(Channel channel, byte[] bytes) {
      this.channel = channel;
      this.bytes = bytes;
    }
  }
}
