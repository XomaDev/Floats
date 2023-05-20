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
import java.util.zip.GZIPOutputStream;

public class FileRequest {

  private static final String TAG = "FileRequest";

  private final InputStream fileInput;
  private final String fileName;
  private final int fileLength;

  private NsdInterface service = null;

  public FileRequest(InputStream fileInput, String fileName, int fileLength) {
    this.fileInput = fileInput;
    this.fileName = fileName;
    this.fileLength = fileLength;
  }

  public void execute(Context context, MultiChannelSystem writer) {
    service = new NsdInterface(context) {
      @Override
      public void accepted() {
        // the other device found the service and now is connected
        try {
          Log.d(TAG, "accepted()");
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
    writer.add(FILE_REQUEST_CHANNEL, requestData);
  }

  private void writeFileContents(InputStream fileInput) throws IOException {
    GZIPOutputStream zipOutputStream = new GZIPOutputStream(service.output);

    byte[] buffer = new byte[BUFFER_SIZE];
    int n;
    while ((n = fileInput.read(buffer)) > 0)
      zipOutputStream.write(buffer, 0, n);

    fileInput.close();

    zipOutputStream.finish();
    zipOutputStream.close();

    service.unregister();
  }
}
