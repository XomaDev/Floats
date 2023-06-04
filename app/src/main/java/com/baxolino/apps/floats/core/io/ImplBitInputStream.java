package com.baxolino.apps.floats.core.io;

import java.io.InputStream;

/**
 * Normal implementation of BitInputStream with
 * nothing much
 */
public class ImplBitInputStream extends BitInputStream {

  public ImplBitInputStream(InputStream stream) {
    super(stream);
  }

  @Override
  public int availableStream() {
    return 0;
  }

  @Override
  public int readStream() {
    return 0;
  }
}
