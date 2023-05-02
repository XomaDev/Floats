package com.baxolino.apps.floats.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.baxolino.apps.floats.FloatsBluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// know-response system class that manages communication
// between two devices and let's each other know when the message
// is delivered
public class KRSystem {

    private static final String TAG = "KRSystem";

    private static final int KNOW_RESPONSE_INT = 1;
    private static final int KNOW_RESPONSE_TIMEOUT = 2000;

    private final InputStream readStream;
    private final OutputStream writeStream;

    public KRSystem(FloatsBluetooth floats) {
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
        int len = bytes.length;

        // sends a two-byte length header data informing
        // about the incoming request

        postLengthHeader(len);
        writeStream.write(bytes);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {

            // so there is some data in the stream
            int read = read();
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

    private int available() {
        try {
            return readStream.available();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int read() {
        try {
            return readStream.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
