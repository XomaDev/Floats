package com.baxolino.apps.floats.core.bytes.io;

import java.io.OutputStream;

public class DummyOutputStream extends OutputStream {
  @Override
  public void write(int b) {
    // a dummy stream we use for testing
  }
}
