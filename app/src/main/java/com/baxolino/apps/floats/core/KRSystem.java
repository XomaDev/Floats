package com.baxolino.apps.floats.core;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.baxolino.apps.floats.NsdFloats;
import com.baxolino.apps.floats.NsdInterface;
import com.baxolino.apps.floats.core.bytes.ChunkConstructor;
import com.baxolino.apps.floats.core.bytes.io.ByteIo;
import com.baxolino.apps.floats.core.bytes.io.DataInputStream;
import com.baxolino.apps.floats.core.bytes.io.BitOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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

  private static final int KNOW_RECEIVE_TIMEOUT = 1000;
  private static final int KNOW_RECEIVE_BACK_TIMEOUT = 3000;


  private static final Channel KNOW_REQUEST_CHANNEL = new Channel((byte) 1);
  private static final Channel FILE_REQUEST_CHANNEL = new Channel((byte) 2);

  private KnowRequestState knowState = KnowRequestState.NONE;

  private static KRSystem krSystem = null;

  public static KRSystem getInstance() {
    if (krSystem != null)
      return krSystem;
    throw new IllegalStateException("KR System Not Initialized");
  }

  public static @NonNull KRSystem getInstance(Context context,
                                     String deviceName,
                                     NsdFloats floats) throws UnknownHostException {
    if (krSystem != null)
      return krSystem;
    return krSystem = new KRSystem(context, deviceName, floats);
  }

  public interface KnowListener {
    void received(String name);

    void timeout();
  }

  public interface FileRequestListener {
    void requested(String name, int length);

    void started();

    void update(int received, int total);
  }


  public final String deviceName;

  private final MultiChannelStream reader;
  private final MultiChannelSystem writer;



  private final int deviceIntIp;

  private String otherDeviceIp = null;

  private KRSystem(Context context, String deviceName, NsdFloats floats) throws UnknownHostException {
    this.deviceName = deviceName;
    reader = new MultiChannelStream(floats.input);
    writer = new MultiChannelSystem(floats.output);

    reader.start();
    writer.start();

    WifiManager wifi = context.getSystemService(WifiManager.class);

    deviceIntIp = wifi.getConnectionInfo().getIpAddress();
    Log.d(TAG, "Device Ip = " + formatIp(deviceIntIp));
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

  public void prepareNsdTransfer(Context context,
                                 String name,
                                 int fileLength,
                                 InputStream fileInput
  ) throws IOException {
    NsdInterface nsdInterface = new NsdInterface(context) {
      @Override
      public void accepted() {
        Log.d(TAG, "Accepted Transfer Request");
        try {
          GZIPOutputStream gZipOut = new GZIPOutputStream(output, ByteIo.BUFFER_SIZE);
          // output is a field in NsdInterface
          ByteIo.copy(fileInput, gZipOut);

          fileInput.close();

          gZipOut.finish();
          gZipOut.close();

          unregister();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      // below method not called since we are not the one
      // requesting connection
      @Override
      public void connected(@NonNull String serviceName) { }
    };
    nsdInterface.registerService(name);
    nsdInterface.initializeServerSocket();

    requestFileTransfer(name, fileLength);
  }

  private void requestFileTransfer(String name, int length) throws IOException {

    // we send the file information before
    // the content
    ChunkConstructor divider = new ChunkConstructor(FILE_REQUEST_CHANNEL.bytes(),
            new BitOutputStream()
                    .write(name.getBytes())
                    .write(Config.FILE_NAME_LENGTH_SEPARATOR)
                    .writeInt32(length)
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

  public void checkFileRequests(Context context, FileRequestListener listener) {
    ByteArrayOutputStream name = new ByteArrayOutputStream();

    DataInputStream requestStream = new DataInputStream();
    requestStream.setByteListener((chunkIndex, b, unsigned) -> {
      if (unsigned == Config.FILE_NAME_LENGTH_SEPARATOR) {
        // now we read the file length
        requestStream.skip(chunkIndex + 1);
        int lengthFile = requestStream.readInt32();


        Log.d(TAG, "Received File Request, name = " + name + " length = "
                + lengthFile);
        listener.requested(name.toString(), lengthFile);
        try {
          receiveContent(context, name.toString(), lengthFile, listener);
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


  private void receiveContent(
          Context context,
          String fileName,
          int total,
          FileRequestListener listener) throws IOException {

    // TODO:
    //  implement notifiers, say waiting for connection
    //  in a dialog
    NsdInterface nsdInterface = new NsdInterface(context) {

      // below method not called since we are the one
      // accepting connection
      @Override
      public void accepted() { }

      @Override
      public void connected(@NonNull String serviceName) {
        Log.d(TAG, "Prepared for receiving");
        listener.started();

        ByteArrayOutputStream received = new ByteArrayOutputStream();
        try {
          GZIPInputStream gZipInput = new GZIPInputStream(input, ByteIo.BUFFER_SIZE);
          // input is a field in NsdInterface
          ByteIo.copy(listener::update, total, gZipInput, received);

          gZipInput.close();

          detach();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
    nsdInterface.discover(fileName);
  }
}
