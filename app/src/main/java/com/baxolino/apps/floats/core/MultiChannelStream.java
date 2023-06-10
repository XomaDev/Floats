package com.baxolino.apps.floats.core;

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

  private final HashMap<ChannelInfo, DataInputStream> channels = new HashMap<>();

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

  public void registerChannelStream(ChannelInfo channelInfo, DataInputStream inputStream) {
    Log.d("KRSystem", "registerChannelStream: register stream = " + Arrays.toString(channelInfo.bytes()));
    DataInputStream stream = channels.get(channelInfo);
    if (stream != null)
      throw new IllegalStateException("Stream already registered = " + channelInfo);
    channels.put(channelInfo, inputStream);
  }


  public void forget(ChannelInfo channelInfo) {
    channels.remove(channelInfo);
  }

  public void start() {
    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    service.scheduleAtFixedRate(() -> {
      if (input.available() > 0) {
        byte[] channel = new byte[Info.CHANNEL_SIZE];
        input.read(channel);

        int blockSize = input.readInt32();
        byte[] chunk = readChunk(blockSize);

        DataInputStream passStream = channels.get(new ChannelInfo(channel));

        if (passStream != null) {
          DataInputStream.ByteListener listener = passStream.getByteListener();
          passStream.addChunk(chunk);
          if (listener != null)
            for (int i = 0; i < blockSize; i++)
              // break when listener returns true
              if (listener.onNewByteAvailable(chunk[i]))
                break;
        } else {
          Log.d("KRSystem", "Stream Not Found = " + Arrays.toString(channel));
        }
      }
    }, 0, 50, TimeUnit.MILLISECONDS);
  }

  private byte[] readChunk(int blockSize) {
    byte[] chunk = new byte[blockSize];
    int read = 0;
    while (read != blockSize)
      read += input.read(chunk);
    return chunk;
  }
}
