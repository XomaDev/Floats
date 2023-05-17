package com.baxolino.apps.floats.core.http;

import android.util.Log;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class HttpSystem {

  private static final int BUFFER_SIZE = 1 << 20;

  public static int initServer(InputStream input) throws IOException {
    int port = SocketUtils.findAvailableTcpPort();

    Log.d("KRSystem", "Creating Server On Port = " + port);

    InetSocketAddress socket = new InetSocketAddress(  port );
    HttpServer server = HttpServer.create(socket, 0);
    server.createContext("/", httpExchange -> {

      httpExchange.sendResponseHeaders(200, input.available());

      OutputStream response = httpExchange.getResponseBody();

      copy(input, response);

      input.close();
      response.close();
    });
    server.setExecutor(null);
    server.start();

    return port;
  }

  private static void copy(InputStream source, OutputStream output)
          throws IOException {
    byte[] buf = new byte[BUFFER_SIZE];
    int n;
    while ((n = source.read(buf)) > 0)
      output.write(buf, 0, n);
  }
}
