package com.baxolino.apps.floats.core;

import static com.baxolino.apps.floats.core.Channel.KNOW_REQUEST_CHANNEL;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.baxolino.apps.floats.NsdFloats;
import com.baxolino.apps.floats.NsdInterface;
import com.baxolino.apps.floats.core.bytes.files.FileRequest;
import com.baxolino.apps.floats.core.bytes.files.RequestHandler;
import com.baxolino.apps.floats.core.bytes.io.BitOutputStream;
import com.baxolino.apps.floats.core.bytes.io.DataInputStream;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    void requested(int requestId, String name, int length);

    void started();

    void update(int received, int total);

    void interupted();
  }

  private final Random random;

  /**
   * Maintains the list of NSD interfaces which are currently used
   * to transfer file contents, maintains their Ids along with objects
   * <p>
   * We maintain a hashmap just in-case we need to use multiple interfaces
   * in the future
   */
  private final HashMap<Integer, NsdInterface> pendingTransfers = new HashMap<>();

  public final String deviceName;

  private final MultiChannelStream reader;
  private final MultiChannelSystem writer;


  private final int deviceIntIp;

  private String otherDeviceIp = null;

  private KRSystem(Context context, String deviceName, NsdFloats floats) throws UnknownHostException {
    random = new Random(deviceName.hashCode());
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

  public void postKnowRequest(String name, Runnable knowSuccessful, Runnable knowFailed) {
    byte[] bytes = name.getBytes();

    byte[] content = new BitOutputStream()
            .writeShort16((short) bytes.length)
            .write(bytes)
            .writeInt32(deviceIntIp)
            .toBytes();
    writer.add(KNOW_REQUEST_CHANNEL, content);

    DataInputStream input = new DataInputStream();
    input.setByteListener((b) -> {
      if (b == KNOW_RESPONSE_INT) {
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

        writer.add(KNOW_REQUEST_CHANNEL, new BitOutputStream()
                .write(KNOW_RESPONSE_INT)
                .writeInt32(deviceIntIp)
                .toBytes());
      }
      reader.forget(KNOW_REQUEST_CHANNEL);
    }, KNOW_RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS);
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

  public void abortNsdTransfer()  {
    // TODO
  }
}
