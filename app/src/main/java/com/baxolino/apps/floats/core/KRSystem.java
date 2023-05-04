package com.baxolino.apps.floats.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.baxolino.apps.floats.FloatsBluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

// know-response system class that manages communication
// between two devices and let's each other know when the message
// is delivered
public class KRSystem {

    private static final String TAG = "KRSystem";

    private static final int KNOW_RESPONSE_INT = 1;
    private static final int KNOW_RESPONSE_TIMEOUT = 2000;

    private static final int LISTEN_DATA_INTERVAL = 500;

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

    private final InputStream readStream;
    private final OutputStream writeStream;

    private KRSystem(String deviceName, FloatsBluetooth floats) {
        this.deviceName = deviceName;
        readStream = floats.getReadStream();
        writeStream = floats.getWriteStream();
    }


    /**
     * Lets the other server device know our device name
     *
     * @param name           Name to be sent
     * @param knowSuccessful called when KR is received
     * @param knowFailed     called when KR is not received with a timeout
     */

    public void postKnowRequest(String name, Runnable knowSuccessful, Runnable knowFailed) throws IOException {
        byte[] bytes = name.getBytes();

        // this sends a two-byte length header before the data

        sendWithHeader(bytes);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {

            // so there is some data in the stream
            int read = readInt();
            boolean done = read == KNOW_RESPONSE_INT;
            if (!done) {
                Log.d(TAG, "Invalid Code = " + read);
            }
            // TODO:
            //  implement state mechanism
            if (done) {
                knowSuccessful.run();
            } else {
                knowFailed.run();
            }
        }, KNOW_RESPONSE_TIMEOUT);
    }

    private int readInt() {
        try {
            return readStream.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendWithHeader(byte[] bytes) throws IOException {
        // send the length of the next incoming data
        postLengthHeader(bytes.length);

        writeStream.write(bytes);
    }

    private void postLengthHeader(int len) throws IOException {
        // 32 bit is unnecessary, so 16 bit

        writeStream.write(len >> 8);
        writeStream.write((byte) len);
    }

    public String readKnowRequest() throws IOException {
        int len = readLengthHeader();

        byte[] allocation = new byte[len];

        int numOfBytesRead = readStream.read(allocation);
        if (numOfBytesRead != len) {
            // device is not connected, or slow connection
            // retry with a buffer

            throw new IOException("Improper Know Request");
        } else {
            // TODO:
            // now let the other device know the message is delivered
            writeStream.write(KNOW_RESPONSE_INT);
            return new String(allocation);
        }
    }

    private int readLengthHeader() throws IOException {
        return ((byte) readStream.read() & 255) << 8 |
                (byte) readStream.read() & 255;
    }

    public void pushStream(String name, InputStream input) throws IOException {
        byte[] bytes = name.getBytes();

        sendWithHeader(bytes);

        // this avoids extra allocation of
        // byte[] objects
        postLengthHeader(input.available());

        // TODO:
        //  while sending large chunks of data, only
        //  some gets delivered so we have to also break
        //  them into segments
        int read;
        while ((read = input.read()) != -1) {
            writeStream.write(read);
        }
        Log.d(TAG, "Data Was Written");
    }

    public void listenIncomingData() {
        new Thread(() -> new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "Checking For Data");
                if (!hasPendingData())
                    return;
                Log.d(TAG, "Handling Incoming Data");
                handleIncomingStream();
            }
        }, 0, LISTEN_DATA_INTERVAL)).start();
    }

    private void handleIncomingStream() {
        String fileName;
        byte[] content;
        try {
            fileName = new String(readWithHeader());
            content = readWithHeader();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Log.d(TAG, "Received File = " + fileName);
        Log.d(TAG, "Content = " + new String(content));
    }

    private byte[] readWithHeader() throws IOException {
        // we don't allocate or read more than we require
        int lengthHeader = readLengthHeader();

        byte[] bytes = new byte[lengthHeader];
        int numOfBytes = 0;


        while (lengthHeader != numOfBytes) {
            numOfBytes += readStream.read(bytes);
            Log.d(TAG, "readWithHeader: " + numOfBytes);
        }
        Log.d(TAG, "readWithHeader: Content = " + new String(bytes));
        return bytes;
    }

    private boolean hasPendingData() {
        try {
            return readStream.available() > 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
