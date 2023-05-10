package com.baxolino.apps.floats.core.bytes;

import static com.baxolino.apps.floats.core.Config.CHANNEL_SIZE;
import static com.baxolino.apps.floats.core.Config.CHUNK_SIZE;
import static com.baxolino.apps.floats.core.Config.SLOTS_ALLOCATION;

import android.util.Log;

import com.baxolino.apps.floats.core.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ChunkConstructor {

    public interface DivisionComplete {
        void onComplete() ;
    }

    private static final String TAG = "ChunkDivider";

    private final byte[] channel;
    private final InputStream input;

    private DivisionComplete listener = null;

    public ChunkConstructor(byte[] channel, byte[] text) {
        this(channel, new ByteArrayInputStream(text));
    }

    public ChunkConstructor(byte[] channel, InputStream input) {
        this.channel = channel;
        this.input = input;
    }

    public void setCompleteListener(DivisionComplete listener) throws IOException {
        this.listener = listener;
        if (listener != null && !pending())
            listener.onComplete();
    }

    public boolean pending() throws IOException {
        Log.d("KRSystem", "Available = " + input.available());
        return input.available() > 0;
    }

    public byte[][] divide() throws IOException {
        int available = input.available();

        if (available >= CHUNK_SIZE)
            Log.d(TAG, "Warning! Know what your doing, input exceeds chunk size");

        int allocateSize = Math.max(available, CHUNK_SIZE) / CHUNK_SIZE;
        if (available % 5 != 0)
            allocateSize++;
        allocateSize = Math.min(allocateSize, SLOTS_ALLOCATION);
        byte[][] chunks = new byte[allocateSize][];

        for (int i = 0; i < allocateSize; i++) {
            available = input.available();
            if (available == 0 || available == -1)
                break;

            chunks[i] = construct(channel, input);
        }
        if (!pending() && listener != null)
            listener.onComplete();
        return chunks;
    }

    public static byte[] construct(byte[] channel, InputStream input) throws IOException {
        int bytesExtra = Config.CHANNEL_SIZE + 2;
        byte[] chunk = new byte[CHUNK_SIZE + bytesExtra];
        // +1 for channel header, +2 for storing the number of blank spots

        // set the channel header
        System.arraycopy(channel, 0, chunk, 0, CHANNEL_SIZE);

        //                                                 @offset 2
        int blankSpots = chunk.length - input.read(chunk, bytesExtra, CHUNK_SIZE) - bytesExtra;


        chunk[4] = (byte) (blankSpots >> 8);
        chunk[5] = (byte) blankSpots;

        Log.d("KRSystem", "divide: " + chunk[4] + " $ " + chunk[5]);
        // unused indexes of @chunk is called a blank spot here
        Log.d("KRSystem", "Blank Spots " + blankSpots);

        return chunk;
    }
}
