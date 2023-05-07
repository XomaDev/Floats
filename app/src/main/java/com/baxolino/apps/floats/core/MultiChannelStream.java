package com.baxolino.apps.floats.core;

import static com.baxolino.apps.floats.core.Config.CHUNK_SIZE;

import android.util.Log;

import com.baxolino.apps.floats.core.bytes.io.BitInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MultiChannelStream {

  public interface Channel {
    void incoming(byte channel, byte[] chunk) throws IOException;
  }

  private final HashMap<Byte, BitInputStream> channels = new HashMap<>();

  private final InputStream input;

  public MultiChannelStream(InputStream input) {
    this.input = input;
  }

  public BitInputStream getChannelStream(byte channel) {
    BitInputStream stream = channels.get(channel);
    if (stream != null)
      return stream;
    stream = new BitInputStream();
    channels.put(channel, stream);
    return stream;
  }

  public void forget(byte channel) {
    channels.remove(channel);
  }

  public void start() {
    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    service.scheduleAtFixedRate(() -> {
      try {
        if (input.available() > 0) {
          byte channel = (byte) input.read();

          byte[] chunk = readChunk();

          BitInputStream listener = channels.get(channel);
          if (listener != null) {
            listener.addChunk(chunk);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }, 0, 500, TimeUnit.MILLISECONDS);
  }

  private byte[] readChunk() throws IOException {
    byte[] chunk = new byte[CHUNK_SIZE];
    int read = 0;
    while (read != CHUNK_SIZE) {
      read += input.read(chunk);
    }
    return chunk;
  }
}
