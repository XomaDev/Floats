package com.baxolino.apps.floats.core;

import android.content.Context;

import androidx.annotation.NonNull;

import com.baxolino.apps.floats.core.files.FileRequest;
import com.baxolino.apps.floats.core.files.RequestHandler;
import com.baxolino.apps.floats.core.transfer.SocketConnection;


public class TaskExecutor {

  public static @NonNull TaskExecutor getInstance(SocketConnection connection) {
    return new TaskExecutor( connection);
  }

  private final MultiChannelStream reader;
  private final MultiChannelSystem writer;

  private TaskExecutor(SocketConnection connection) {

    reader = new MultiChannelStream(connection.input);
    writer = new MultiChannelSystem(connection.output);

    reader.start();
    writer.start();
  }

  public void execute(Context context, FileRequest fileRequest) {
    fileRequest.execute(context, writer);
  }

  // called when Session class is started
  // this looks for incoming file requests that contains
  // the file name followed by the file length

  public void register(RequestHandler handler) {
    handler.setReader(reader);
  }
}
