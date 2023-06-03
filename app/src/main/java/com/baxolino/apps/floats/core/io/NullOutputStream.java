package com.baxolino.apps.floats.core.io;

import java.io.OutputStream;

public class NullOutputStream extends OutputStream {
  @Override
  public void write(int b) {
    // a dummy stream we use for testing
  }
}
