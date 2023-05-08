package com.baxolino.apps.floats.core;

import android.util.Log;
import android.util.Pair;

import com.baxolino.apps.floats.FloatsBluetooth;
import com.baxolino.apps.floats.core.bytes.ChunkDivider;
import com.baxolino.apps.floats.core.bytes.io.BitInputStream;
import com.baxolino.apps.floats.core.bytes.io.BitOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private static final int KNOW_RECEIVE_BACK_TIMEOUT = 5000;

    private static final int LOOK_FILE_REQUEST_INTERVAL = 200;

    private static final byte KNOW_REQUEST_CHANNEL = 1;
    private static final byte FILE_REQUEST_CHANNEL = 2;

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

    public final String deviceName;

    private final MultiChannelStream reader;
    private final MultiChannelSystem writer;

    private KRSystem(String deviceName, FloatsBluetooth floats) {
        this.deviceName = deviceName;
        reader = new MultiChannelStream(floats.getReadStream());
        writer = new MultiChannelSystem(floats.getWriteStream());

        reader.start();
        writer.start();
    }

    public void postKnowRequest(String name, Runnable knowSuccessful, Runnable knowFailed) throws IOException {
        byte[] bytes = name.getBytes();
        ChunkDivider divider = new ChunkDivider(KNOW_REQUEST_CHANNEL,
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

        BitInputStream input = reader.getChannelStream(KNOW_REQUEST_CHANNEL);
        input.setChunkListener(() -> {
            int read = input.read();
            if (read == KNOW_RESPONSE_INT) {
                knowSuccessful.run();
                knowState = KnowRequestState.SUCCESS;
            } else {
                Log.d(TAG, "Received Unexpected: " + read);
                knowState = KnowRequestState.FAILED;
                knowFailed.run();
            }
            // @Important if we use the same channel
            // again
            input.flushCurrent();
        });

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
        BitInputStream input = reader.getChannelStream(KNOW_REQUEST_CHANNEL);
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

                ChunkDivider divider = new ChunkDivider(KNOW_REQUEST_CHANNEL,
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

    public void requestFileTransfer(String name, int length) throws IOException {
        ChunkDivider divider = new ChunkDivider(FILE_REQUEST_CHANNEL,
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

    public void checkFileRequests() {
        BitInputStream requestsStream = reader.getChannelStream(FILE_REQUEST_CHANNEL);
        ScheduledExecutorService service = Executors.newScheduledThreadPool(2);
        service.scheduleAtFixedRate(() -> {
            if (requestsStream.reachedEOS())
                return;
            service.schedule(() -> {
                // TODO:
                //  we need to create a more effective system
                //  where we can avoid again scheduling a delay
                //  to read the incoming data

                // basically wait for the complete data to arrive
                Pair<String, Integer> request = readFileRequest(requestsStream);
                Log.d(TAG, "File Request: " + request);
            }, LOOK_FILE_REQUEST_INTERVAL * 10, TimeUnit.MILLISECONDS);

        }, 0, LOOK_FILE_REQUEST_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private Pair<String, Integer> readFileRequest(BitInputStream stream) {
        if (stream.reachedEOS())
            throw new RuntimeException("No Data Found");

        ByteArrayOutputStream fileName = new ByteArrayOutputStream();
        int read;
        while ((read = stream.read()) != Config.FILE_NAME_LENGTH_SEPARATOR && read != -1)
            fileName.write(read);

        int length = stream.readInt32();

        // @Important
        // sometimes there can be some leftover null bytes,
        // this will just skip it

        stream.flushCurrent();
        return new Pair<>(fileName.toString(), length);
    }
}
