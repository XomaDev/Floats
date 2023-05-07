package com.baxolino.apps.floats.core;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MultiChannelSystem {

  public enum Priority {
    NORMAL, TOP
  }

  public interface ChannelStatusListener {
    void onNeedRefill() throws IOException;
  }

  private final OutputStream stream;

  private final ArrayList<byte[]> byteChunks = new ArrayList<>();

  private final ArrayList<ChannelStatusListener> listeners = new ArrayList<>();

  public MultiChannelSystem(OutputStream stream) {
    this.stream = stream;
  }

  public void addRefillListener(ChannelStatusListener listener) {
    listeners.add(listener);
  }

  public MultiChannelSystem add(byte[][] chunks, Priority priority) {
    List<byte[]> bytes = Arrays.asList(chunks);

    if (priority == Priority.NORMAL) {
      // add it at the end
      byteChunks.addAll(bytes);
    } else if (priority == Priority.TOP) {
      byteChunks.addAll(0, bytes);
    }
    return this;
  }

  public void start() {
    Log.d("KRSystem", "start()");
    for (;;) {
      if (write()) {
        postRefillListeners();
        // tries to write again
        if (write()) {
          // we will check if there is any other
          // pending data after 1 second
          ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
          service.schedule(this::start, 1, TimeUnit.SECONDS);
          Log.d("KRSystem", "Restarting");
          break;
        }
      }
    }
  }

  private void postRefillListeners() {
    try {
      for (ChannelStatusListener listener : listeners) {
        listener.onNeedRefill();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean write() {
    if (byteChunks.isEmpty())
      return true;
    byte[] poll = byteChunks.remove(0);
    if (poll != null) {
      try {
        stream.write(poll);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return poll == null;
  }
}
