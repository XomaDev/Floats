package com.baxolino.apps.floats.core;

import android.util.Log;

import com.baxolino.apps.floats.FloatsBluetooth;
import com.baxolino.apps.floats.core.bytes.ChunkConstructor;
import com.baxolino.apps.floats.core.bytes.io.DataInputStream;
import com.baxolino.apps.floats.core.bytes.io.BitOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// know-response system class that manages communication
// between two devices and let's each other know when the message
// is delivered
public class KRSystem {

  private static final String TAG = "KRSystem";

  public interface KnowListener {
    void received(String name);

    void timeout();
  }

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

  public static KRSystem getInstance(String deviceName,
                                     FloatsBluetooth floats) {
    if (krSystem != null)
      return krSystem;
    return krSystem = new KRSystem(deviceName, floats);
  }

  private final Random random;

  public final String deviceName;

  private final MultiChannelStream reader;
  private final MultiChannelSystem writer;

  private KRSystem(String deviceName, FloatsBluetooth floats) {
    this.deviceName = deviceName;
    reader = new MultiChannelStream(floats.getReadStream());
    writer = new MultiChannelSystem(floats.getWriteStream());

    reader.start();
    writer.start();
    random = new Random(deviceName.hashCode());
  }

  public void postKnowRequest(String name, Runnable knowSuccessful, Runnable knowFailed) throws IOException {
    byte[] bytes = name.getBytes();
    ChunkConstructor divider = new ChunkConstructor(KNOW_REQUEST_CHANNEL.bytes(),
            // we send a 16 bit number representing length
            // of next oncoming bytes, i.e device name
            new BitOutputStream()
                    .writeShort16((short) bytes.length)
                    .write(bytes)
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

    DataInputStream input = new DataInputStream()
            .setByteListener((chunkIndex, b, unsigned) -> {
              if (unsigned == KNOW_RESPONSE_INT) {
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
        // @Important if we want to use same channel again
        input.flushCurrent();

        Log.d(TAG, "Received Device Name = " + new String(bytes));
        listener.received(new String(bytes));

        // now we have to send a request back
        // saying received

        ChunkConstructor divider = new ChunkConstructor(KNOW_REQUEST_CHANNEL.bytes(),
                new BitOutputStream()
                        .write(KNOW_RESPONSE_INT)
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

  public void requestFileTransfer(InputStream input, String name, int length) throws IOException {
    byte[] requestId = new byte[Config.FILE_NAME_LENGTH_SEPARATOR];
    random.nextBytes(requestId);

    // we send the file information before
    // the content
    ChunkConstructor divider = new ChunkConstructor(FILE_REQUEST_CHANNEL.bytes(),
            new BitOutputStream()
                    .write(name.getBytes())
                    .write(Config.FILE_NAME_LENGTH_SEPARATOR)
                    .writeInt32(length)
                    .write(requestId)
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

    divider.setCompleteListener(() -> writeContentFromStream(requestId, input));
  }

  private void writeContentFromStream(byte[] channelId, InputStream input) {
    // divide the bytes into the sizes of each chunk
    // then send the bytes
    new Thread(() -> {
      try {
        while (input.available() > 0) {
          byte[] chunk = ChunkConstructor.construct(channelId, input);
          writer.add(chunk, MultiChannelSystem.Priority.NORMAL);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).start();
  }

  // called when Session class is started
  // this looks for incoming file requests that contains
  // the file name followed by the file length

  public void checkFileRequests() {
    ByteArrayOutputStream name = new ByteArrayOutputStream();

    DataInputStream requestStream = new DataInputStream();
    requestStream.setByteListener((chunkIndex, b, unsigned) -> {
      if (unsigned == Config.FILE_NAME_LENGTH_SEPARATOR) {
        // now we read the file length
        requestStream.skip(chunkIndex + 1);
        int lengthFile = requestStream.readInt32();

        // file request id, in form of bytes
        byte[] requestId = new byte[Config.CHANNEL_SIZE];
        requestStream.read(requestId);

        Log.d(TAG, "Received File Request, name = " + name + " length = "
                + lengthFile + " id = " + new String(requestId));
        receiveContent(name.toString(), lengthFile, requestId);
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

  private void receiveContent(String name, int length, byte[] channelId) {
    DataInputStream input = new DataInputStream();
    Log.d(TAG, "receiveContent: try register = " + Arrays.toString(channelId));
    reader.registerChannelStream(new Channel(channelId), input);

    AtomicInteger n = new AtomicInteger();
    input.setByteListener((byteIndex, b, unsigned) -> {
      Log.d(TAG, "Receive = " + (n.incrementAndGet()) + " / " + length);
      return false;
    });
  }
}
