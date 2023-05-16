package com.baxolino.apps.floats.core;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.baxolino.apps.floats.FloatsBluetooth;
import com.baxolino.apps.floats.core.bytes.ChunkConstructor;
import com.baxolino.apps.floats.core.bytes.io.DataInputStream;
import com.baxolino.apps.floats.core.bytes.io.BitOutputStream;
import com.baxolino.apps.floats.core.http.HttpSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// know-response system class that manages communication
// between two devices and let's each other know when the message
// is delivered
public class KRSystem {

  private static final String TAG = "KRSystem";

  enum KnowRequestState {
    NONE, SUCCESS, FAILED
  }


  // a success code sent back by know-request receiver
  private static final byte KNOW_RESPONSE_INT = 1;

  private static final int KNOW_RECEIVE_TIMEOUT = 2000;
  private static final int KNOW_RECEIVE_BACK_TIMEOUT = 6000;


  private static final Channel KNOW_REQUEST_CHANNEL = new Channel((byte) 1);
  private static final Channel FILE_REQUEST_CHANNEL = new Channel((byte) 2);

  private KnowRequestState knowState = KnowRequestState.NONE;

  private static KRSystem krSystem = null;

  public static KRSystem getInstance() {
    if (krSystem != null)
      return krSystem;
    throw new IllegalStateException("KR System Not Initialized");
  }

  public static KRSystem getInstance(Context context,
                                     String deviceName,
                                     FloatsBluetooth floats) throws UnknownHostException {
    if (krSystem != null)
      return krSystem;
    return krSystem = new KRSystem(context, deviceName, floats);
  }

  public interface KnowListener {
    void received(String name);

    void timeout();
  }

  public interface FileRequestListener {
    void request(String name, int length);

    void update(int received, int total);
  }



  public final String deviceName;

  private final MultiChannelStream reader;
  private final MultiChannelSystem writer;

  private final OkHttpClient client = new OkHttpClient();


  private final int deviceIntIp;

  private final String deviceIp;
  private String otherDeviceIp = null;

  private KRSystem(Context context, String deviceName, FloatsBluetooth floats) throws UnknownHostException {
    this.deviceName = deviceName;
    reader = new MultiChannelStream(floats.getReadStream());
    writer = new MultiChannelSystem(floats.getWriteStream());

    reader.start();
    writer.start();

    WifiManager wifi = context.getSystemService(WifiManager.class);

    deviceIntIp = wifi.getConnectionInfo().getIpAddress();
    deviceIp = formatIp(deviceIntIp);

    Log.d(TAG, "Device Ip = " + deviceIp);
  }

  private String formatIp(int intIp) throws UnknownHostException {
    return InetAddress.getByAddress(
                    ByteBuffer
                            .allocate(Integer.BYTES)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(intIp)
                            .array())
            .getHostAddress();
  }

  public void postKnowRequest(String name, Runnable knowSuccessful, Runnable knowFailed) throws IOException {
    byte[] bytes = name.getBytes();
    ChunkConstructor divider = new ChunkConstructor(KNOW_REQUEST_CHANNEL.bytes(),
            // we send a 16 bit number representing length
            // of next oncoming bytes, i.e device name
            new BitOutputStream()
                    .writeShort16((short) bytes.length)
                    .write(bytes)
                    .writeInt32(deviceIntIp)
                    .toBytes());
    writer.add(
            divider.divide(),
            MultiChannelSystem.Priority.TOP
    ).addRefillListener(() -> {
      // add the next part of bytes
      if (divider.pending()) {
        writer.add(divider.divide(), MultiChannelSystem.Priority.TOP);
      }
    });

    DataInputStream input = new DataInputStream();
    input.setByteListener((chunkIndex, b, unsigned) -> {
      if (unsigned == KNOW_RESPONSE_INT) {
        input.skip(1);
        try {
          otherDeviceIp = formatIp(input.readInt32());
          Log.d(TAG, "Received Other Device Ip = " + otherDeviceIp);
        } catch (UnknownHostException e) {
          throw new RuntimeException(e);
        }

        knowState = KnowRequestState.SUCCESS;
        knowSuccessful.run();
      } else {
        // we received invalid message
        knowState = KnowRequestState.FAILED;
        knowFailed.run();
      }
      // true because we just care about the first byte
      return true;
    });
    reader.registerChannelStream(KNOW_REQUEST_CHANNEL, input);

    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    service.schedule(() -> {
      // we do not check for == FAILED
      // because it's already dispatched then
      if (knowState == KnowRequestState.NONE) {
        knowFailed.run();
        return;
      }
      reader.forget(KNOW_REQUEST_CHANNEL);
    }, KNOW_RECEIVE_BACK_TIMEOUT, TimeUnit.MILLISECONDS);
  }

  public void readKnowRequest(KnowListener listener) {
    DataInputStream input = new DataInputStream();
    reader.registerChannelStream(KNOW_REQUEST_CHANNEL, input);

    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    service.schedule(() -> {
      if (input.reachedEOS()) {
        listener.timeout();
        reader.forget(KNOW_REQUEST_CHANNEL);
        return;
      }
      int lenContent = input.readShort16();
      byte[] bytes = new byte[lenContent];

      if (input.read(bytes) != lenContent) {
        // we did not receive number of bytes
        // we expected
        listener.timeout();
      } else {
        try {
          otherDeviceIp = formatIp(input.readInt32());
          Log.d(TAG, "Received Other Device Id = " + otherDeviceIp);
        } catch (UnknownHostException e) {
          throw new RuntimeException(e);
        }

        // @Important if we want to use same channel again
        input.flushCurrent();

        Log.d(TAG, "Received Device Name = " + new String(bytes));
        listener.received(new String(bytes));

        // now we have to send a request back
        // saying received

        ChunkConstructor divider = new ChunkConstructor(KNOW_REQUEST_CHANNEL.bytes(),
                new BitOutputStream()
                        .write(KNOW_RESPONSE_INT)
                        .writeInt32(deviceIntIp)
                        .toBytes());
        byte[][] divided;
        try {
          divided = divider.divide();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        writer.add(divided, MultiChannelSystem.Priority.TOP);
      }
      reader.forget(KNOW_REQUEST_CHANNEL);
    }, KNOW_RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS);
  }

  // TODO:
  //  send the http server id
  //  from where the receiver can download it

  public void prepareHttpTransfer(String name, int fileLength, InputStream stream) throws IOException {
    // this is a port where we use to transfer the
    // files
    int portTransfer = HttpSystem.initServer(stream);

    requestFileTransfer(portTransfer, name, fileLength);
  }

  private void requestFileTransfer(int port, String name, int length) throws IOException {

    // we send the file information before
    // the content
    ChunkConstructor divider = new ChunkConstructor(FILE_REQUEST_CHANNEL.bytes(),
            new BitOutputStream()
                    .write(name.getBytes())
                    .write(Config.FILE_NAME_LENGTH_SEPARATOR)
                    .writeInt32(length)
                    .writeInt32(port)
                    .toBytes());

    writer.add(
            divider.divide(),
            MultiChannelSystem.Priority.TOP
    ).addRefillListener(() -> {
      // add the next part of bytes
      if (divider.pending()) {
        writer.add(divider.divide(), MultiChannelSystem.Priority.TOP);
      }
    });
  }

  // called when Session class is started
  // this looks for incoming file requests that contains
  // the file name followed by the file length

  public void checkFileRequests(FileRequestListener listener) {
    ByteArrayOutputStream name = new ByteArrayOutputStream();

    DataInputStream requestStream = new DataInputStream();
    requestStream.setByteListener((chunkIndex, b, unsigned) -> {
      if (unsigned == Config.FILE_NAME_LENGTH_SEPARATOR) {
        // now we read the file length
        requestStream.skip(chunkIndex + 1);
        int lengthFile = requestStream.readInt32();

        // file request id, in form of bytes
        int port = requestStream.readInt32();

        Log.d(TAG, "Received File Request, name = " + name + " length = "
                + lengthFile + " port = " + port);
        listener.request(name.toString(), lengthFile);
        try {
          receiveContent(port, lengthFile, listener);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        // reset the name array
        name.reset();
        requestStream.flushCurrent();
        return true;
      }
      name.write(b);
      return false;
    });

    reader.registerChannelStream(FILE_REQUEST_CHANNEL, requestStream);
  }


  private void receiveContent(int port, int total, FileRequestListener listener) throws IOException {
    try {
      String urlString = "http://" + otherDeviceIp + ":" + port;
      Log.d(TAG, "Receive Content = " + urlString);
      URL url = new URL(urlString);

      Request request = new Request.Builder()
              .url(url)
              .build();

      try (Response response = client.newCall(request).execute()) {
        Log.d(TAG, "receiveContent: " + response.code());
        copy(total, listener, response.body().byteStream(), new ByteArrayOutputStream());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void copy(int total, FileRequestListener listener, InputStream source, OutputStream output)
          throws IOException {
    byte[] buf = new byte[BUFFER_SIZE];
    int written = 0;
    int n;
    while ((n = source.read(buf)) > 0) {
      output.write(buf, 0, n);
      written += n;
      listener.update(written, total);
    }
  }

  private static final int BUFFER_SIZE = 1 << 25;

}
