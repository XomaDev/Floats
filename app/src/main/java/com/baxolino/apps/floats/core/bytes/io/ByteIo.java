package com.baxolino.apps.floats.core.bytes.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class ByteIo {

  public interface Listener {
    void update(int transferred, int total);
  }

  public static final int BUFFER_SIZE = 1 << 22;

  public static void copy(InputStream source, OutputStream output)
          throws IOException {
    byte[] buf = new byte[BUFFER_SIZE];
    int n;
    while ((n = source.read(buf)) > 0)
      output.write(buf, 0, n);
  }

  public static void copy(Listener listener, int total,
                          InputStream source, OutputStream output)
          throws IOException {
    byte[] buf = new byte[BUFFER_SIZE];
    int n, written = 0;

    while ((n = source.read(buf)) > 0) {
      output.write(buf, 0, n);
      written += n;
      listener.update(written, total);
    }
  }
}
