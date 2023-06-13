package com.baxolino.apps.floats.core.transfer;

import com.baxolino.apps.floats.core.io.BitOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MultiChannelSystem {

  private boolean running = true;

  private final BitOutputStream stream;

  private final ArrayList<ByteChunk> byteChunks = new ArrayList<>();

  public MultiChannelSystem(OutputStream stream) {
    this.stream = new BitOutputStream(stream);
  }

  public void write(ChannelInfo channelInfo, byte[] bytes) {
    byteChunks.add(new ByteChunk(channelInfo, bytes));
  }

  public void start() {
    for (;;) {
      if (write()) {
        // tries to write again
        if (write() && running) {
          // we will check if there is any other
          // pending data after 1 second
          ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
          service.schedule(() -> {
            service.shutdownNow();
            start();
          }, 70, TimeUnit.MILLISECONDS);
          break;
        }
      }
    }
  }

  public void stop() {
    running = false;
  }

  private boolean write() {
    if (byteChunks.isEmpty())
      return true;
    ByteChunk chunk = byteChunks.remove(0);
    try {
      // the channel header
      stream.write(chunk.channelInfo.bytes());
      int blockSize = chunk.bytes.length;

      stream.writeInt32(blockSize);

      stream.write(chunk.bytes);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return false;
  }

  static class ByteChunk {

    private final ChannelInfo channelInfo;
    private final byte[] bytes;

    public ByteChunk(ChannelInfo channelInfo, byte[] bytes) {
      this.channelInfo = channelInfo;
      this.bytes = bytes;
    }
  }
}
