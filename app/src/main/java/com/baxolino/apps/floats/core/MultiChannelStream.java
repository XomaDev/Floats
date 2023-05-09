package com.baxolino.apps.floats.core;

import static com.baxolino.apps.floats.core.Config.CHUNK_SIZE;

import com.baxolino.apps.floats.core.bytes.io.BitInputStream;
import com.baxolino.apps.floats.core.bytes.io.DataInputStream;

import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MultiChannelStream {

  private final HashMap<Byte, DataInputStream> channels = new HashMap<>();

  private final BitInputStream input;

  public MultiChannelStream(InputStream input) {
    this.input = new BitInputStream(input) {
      @Override
      public int availableStream() {
        // this method is never called since we
        // are passing a valid stream to the constructor
        return 0;
      }

      @Override
      public int readStream() {
        // same description as the method overridden
        // above
        return -1;
      }
    };
  }

  public void registerChannelStream(byte channel, DataInputStream inputStream) {
    DataInputStream stream = channels.get(channel);
    if (stream != null)
      throw new IllegalStateException("Stream already registered = " + channel);
    channels.put(channel, inputStream);
  }


  public void forget(byte channel) {
    channels.remove(channel);
  }

  public void start() {
    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    service.scheduleAtFixedRate(() -> {
      if (input.available() > 0) {
        byte channel = (byte) input.read();

        int blankSpots = input.readShort16();
        byte[] chunk = readChunk(blankSpots);

        DataInputStream passStream = channels.get(channel);

        if (passStream != null) {
          DataInputStream.ByteListener listener = passStream.getByteListener();
          passStream.addChunk(chunk);
          if (listener != null)
            for (int i = 0, len = CHUNK_SIZE - blankSpots; i < len; i++)
              // break when listener returns true
              if (listener.onNewByteAvailable(i, chunk[i], chunk[i] & 0xff))
                break;
        }
      }
    }, 0, 500, TimeUnit.MILLISECONDS);
  }

  private byte[] readChunk(int blankSpots) {
    int allocateSize = CHUNK_SIZE - blankSpots;
    byte[] chunk = new byte[allocateSize];
    int read = 0;
    // TODO:
    // read how much blank spots and only allocate
    // whats required here
    while (read != allocateSize)
      read += input.read(chunk);

    //noinspection ResultOfMethodCallIgnored
    input.skip(blankSpots);
    return chunk;
  }
}
