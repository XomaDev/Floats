package com.baxolino.apps.floats.core.bytes.files;

import static com.baxolino.apps.floats.core.Config.BUFFER_SIZE;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.baxolino.apps.floats.NsdInterface;
import com.baxolino.apps.floats.core.bytes.io.DummyOutputStream;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class FileReceiver {

  private static final String TAG = "FileReceiver";

  public interface StartedListener {
    void started();
  }

  public interface UpdateListener {
    void update(int received);
  }

  public interface FinishedListener {
    void finished();
  }

  public final String name;
  public final int length;

  private NsdInterface service;

  private StartedListener startListener;
  private UpdateListener updateListener;

  private FinishedListener finishedListener;

  private boolean cancelled = false;

  public long startTime;

  FileReceiver(String name, int length) {
    this.name = name;
    this.length = length;
  }

  public void setStartListener(StartedListener listener) {
    Log.d(TAG, "setStartListener: called");
    startListener = listener;
    Log.d(TAG, "setStartListener: set");
  }

  public void setUpdateListener(UpdateListener listener) {
    updateListener = listener;
  }

  public void setFinishedListener(FinishedListener listener) {
    finishedListener = listener;
  }

  public void cancel() {
    // a simple cancel message to stop sending
    // more data
    new Thread(() -> {
      try {
        service.output.write(Reasons.REASON_CANCELED);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }).start();

    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    service.schedule(() -> {
      // setting this property first will cause
      // interruption when the sender writes data,

      // then it'll check for any messages in it's input stream
      // and there we'll post a code with a reason
      cancelled = true;
    }, 60, TimeUnit.MILLISECONDS);
  }

  public void receive(Context context) {
    Log.d(TAG, "Receiving");
    service = new NsdInterface(context) {
      @Override
      public void accepted() {
        // method not called since we are the one
        // accepting connection
      }

      @Override
      public void connected(@NonNull String serviceName) {
        Log.d(TAG, "Connected");
        startListener.started();
        startTime = System.currentTimeMillis();

        try {
          receiveContents();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
    service.discover(name);
  }

  private void receiveContents() throws IOException {
    DummyOutputStream output = new DummyOutputStream();
    GZIPInputStream zipInput = new GZIPInputStream(service.input, BUFFER_SIZE);

    byte[] buffer = new byte[BUFFER_SIZE];
    int n, received = 0;

    while (!cancelled && (n = zipInput.read(buffer)) > 0) {
      output.write(buffer, 0, n);
      received += n;
      updateListener.update(received);
    }

    zipInput.close();
    service.detach();

    finishedListener.finished();
  }
}
