package com.baxolino.apps.floats.core;

import android.util.Log;

import com.baxolino.apps.floats.FloatsBluetooth;
import com.baxolino.apps.floats.core.bytes.ChunkDivider;
import com.baxolino.apps.floats.core.bytes.io.BitOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    }


    // a success code sent back by know-request receiver
    private static final byte KNOW_RESPONSE_INT = 1;
    private static final int KNOW_RESPONSE_TIMEOUT = 5000;

    private static final byte KNOW_REQUEST_CHANNEL = 1;

    private boolean knowRequestDone = false;

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
                new BitOutputStream()
                        .writeShort16((short) bytes.length)
                        .write(bytes)
                        .toBytes());
        writer.add(
                divider.divide(),
                MultiChannelSystem.Priority.TOP
        ).addRefillListener(() -> {
            // add the next part of bytes
            if (divider.pending())
                writer.add(divider.divide(), MultiChannelSystem.Priority.TOP);
        });

        reader.listen(KNOW_REQUEST_CHANNEL, (channel, chunk) -> {
            if (chunk[0] == KNOW_RESPONSE_INT) {
                knowRequestDone = true;
                knowSuccessful.run();

                // unregister the response listener
                reader.forget(KNOW_REQUEST_CHANNEL);
            } else {
                Log.d("HomeActivity", "Received different response");
            }
        });
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        service.schedule(() -> {
            if (!knowRequestDone)
                knowFailed.run();

            // unregister the response listener
            reader.forget(KNOW_REQUEST_CHANNEL);
        }, KNOW_RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    public void readKnowRequest(KnowListener listener) {

        reader.listen(KNOW_REQUEST_CHANNEL, (channel, chunk) -> {
            String name = new String(chunk);
            listener.received(name);

            // sends a receive success request back
            writer.add(
                    ChunkDivider.oneChunk(KNOW_RESPONSE_INT),
                    MultiChannelSystem.Priority.TOP
            );

            // unregisters the listener
            reader.forget(KNOW_REQUEST_CHANNEL);
        });
    }
}
