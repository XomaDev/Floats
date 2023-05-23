package com.baxolino.apps.floats.core.bytes.files;

import static com.baxolino.apps.floats.core.Channel.FILE_REQUEST_CHANNEL;
import static com.baxolino.apps.floats.core.Config.BUFFER_SIZE;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.baxolino.apps.floats.NsdInterface;
import com.baxolino.apps.floats.core.MultiChannelSystem;
import com.baxolino.apps.floats.core.bytes.io.BitOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

public class FileRequest {

  public interface CancelListener {
    void cancelled(int reason);
  }

  private static final String TAG = "FileRequest";

  private final InputStream fileInput;
  private final String fileName;
  private final int fileLength;

  private NsdInterface service = null;

  private boolean cancelled = false;

  private CancelListener cancelListener;

  public FileRequest(InputStream fileInput, String fileName, int fileLength) {
    this.fileInput = fileInput;
    this.fileName = fileName;
    this.fileLength = fileLength;
  }

  public void setCancelListener(CancelListener listener) {
    cancelListener = listener;
  }

  public void execute(Context context, MultiChannelSystem writer) {
    service = new NsdInterface(context) {
      @Override
      public void accepted() {
        // the other device found the service and now is connected
        try {
          Log.d(TAG, "accepted()");

          lookCancelRequests();
          writeFileContents(fileInput);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void connected(@NonNull String serviceName) {
        // not invoked, since we are not the one making the request
      }
    };
    // register as the name of the file
    service.registerService(fileName);


    byte[] fileNameBytes = fileName.getBytes();

    byte[] requestData = new BitOutputStream()
            .writeInt32(fileLength)
            .writeInt32(fileNameBytes.length)
            .write(fileNameBytes)
            .toBytes();
    writer.write(FILE_REQUEST_CHANNEL, requestData);
  }

  private void writeFileContents(InputStream fileInput) throws IOException {
    GZIPOutputStream zipOutputStream = new GZIPOutputStream(service.output);

    byte[] buffer = new byte[BUFFER_SIZE];
    int n;
    while (!cancelled && (n = fileInput.read(buffer)) > 0) {
      try {
        zipOutputStream.write(buffer, 0, n);
      } catch (SocketException e) {
        // BrokenPipe: this error is thrown when the receiver abruptly
        // closes the connection, we have few ms before the client is fully closed
        Log.d(TAG, "Failed while writing, available = " + service.input.read());
        cancelled = true;
        break;
      }
    }

    fileInput.close();

    // without this check, it will throw SocketException
    if (!cancelled) {
      zipOutputStream.finish();
      zipOutputStream.close();
    }

    service.unregister();
  }

  private void lookCancelRequests() {
    InputStream input = service.input;

    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    service.scheduleAtFixedRate(() -> {
      try {
        if (input.available() > 0) {

          cancelled = true;
          Log.d(TAG, "lookCancelRequests: Transfer was cancelled");

          // receiver sends a cancel code before it completely cuts off
          // the connection in few ms
          int reason = input.read();

          if (cancelListener != null) {
            cancelListener.cancelled(reason);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }, 0, 20, TimeUnit.MILLISECONDS);
  }
}
