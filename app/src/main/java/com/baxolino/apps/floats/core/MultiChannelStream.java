package com.baxolino.apps.floats.core;

import static com.baxolino.apps.floats.core.Config.CHUNK_SIZE;

import android.util.Log;

import com.baxolino.apps.floats.core.io.BitInputStream;
import com.baxolino.apps.floats.core.io.DataInputStream;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MultiChannelStream {

  private final HashMap<Channel, DataInputStream> channels = new HashMap<>();

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

  public void registerChannelStream(Channel channel, DataInputStream inputStream) {
    Log.d("KRSystem", "registerChannelStream: register stream = " + Arrays.toString(channel.bytes()));
    DataInputStream stream = channels.get(channel);
    if (stream != null)
      throw new IllegalStateException("Stream already registered = " + channel);
    channels.put(channel, inputStream);
  }


  public void forget(Channel channel) {
    channels.remove(channel);
  }

  public void start() {
    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    service.scheduleAtFixedRate(() -> {
      if (input.available() > 0) {
        byte[] channel = new byte[Config.CHANNEL_SIZE];
        input.read(channel);

        int blankSpots = input.readShort16();
        byte[] chunk = readChunk(blankSpots);

        DataInputStream passStream = channels.get(new Channel(channel));

        if (passStream != null) {
          DataInputStream.ByteListener listener = passStream.getByteListener();
          passStream.addChunk(chunk);
          if (listener != null)
            for (int i = 0, len = CHUNK_SIZE - blankSpots; i < len; i++)
              // break when listener returns true
              if (listener.onNewByteAvailable(chunk[i]))
                break;
        } else {
          Log.d("KRSystem", "Stream Not Found = " + Arrays.toString(channel));
        }
      }
    }, 0, 50, TimeUnit.MILLISECONDS);
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
