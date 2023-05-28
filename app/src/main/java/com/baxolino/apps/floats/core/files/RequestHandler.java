package com.baxolino.apps.floats.core.files;

import com.baxolino.apps.floats.core.Channel;
import com.baxolino.apps.floats.core.MultiChannelStream;
import com.baxolino.apps.floats.core.io.DataInputStream;

public class RequestHandler {

  public interface RequestsListener {
    void requested(FileReceiver receiver);
  }

  private final RequestsListener listener;

  private MultiChannelStream reader;

  public RequestHandler(RequestsListener listener) {
    this.listener = listener;
  }

  public void setReader(MultiChannelStream reader) {
    this.reader = reader;
    init();
  }

  private void init() {
    DataInputStream requests = new DataInputStream();
    requests.setByteListener(b -> {
      int lengthOfFile = requests.readInt32();
      int nameStringLength = requests.readInt32();

      byte[] fileName = new byte[nameStringLength];
      requests.read(fileName);

      String name = new String(fileName);

      // true because, we do not need rest of the
      // byte listen calls
      listener.requested(new FileReceiver(name, lengthOfFile));
      return true;
    });
    reader.registerChannelStream(Channel.FILE_REQUEST_CHANNEL, requests);
  }
}
