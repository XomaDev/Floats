package com.baxolino.apps.floats.core.bytes;

import static com.baxolino.apps.floats.core.Config.CHUNK_SIZE;
import static com.baxolino.apps.floats.core.Config.SLOTS_ALLOCATION;

import android.util.Log;

import com.baxolino.apps.floats.core.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class ChunkDivider {

    public static byte[][] oneChunk(byte... bytes) {
        int len = bytes.length;
        if (len > Config.CHUNK_SIZE) {
            throw new RuntimeException("Exceeds Chunk Size (" + Config.CHUNK_SIZE + ")");
        }
        byte[] chunk = new byte[Config.CHUNK_SIZE];
        System.arraycopy(bytes, 0, chunk, 0, len);
        return new byte[][] { chunk };
    }

    private final byte channel;
    private final InputStream input;

    public ChunkDivider(byte channel, byte[] text) {
        this(channel, new ByteArrayInputStream(text));
    }

    public ChunkDivider(byte channel, InputStream input) {
        this.channel = channel;
        this.input = input;
    }

    public boolean pending() throws IOException {
        Log.d("KRSystem", "Available = " + input.available());
        return input.available() > 0;
    }

    public byte[][] divide() throws IOException {
        int available = input.available();
        int allocateSize = Math.max(available, CHUNK_SIZE) / CHUNK_SIZE;
        if (available % 5 != 0)
            allocateSize++;
        allocateSize = Math.min(allocateSize, SLOTS_ALLOCATION);
        byte[][] chunks = new byte[allocateSize][];

        for (int i = 0; i < allocateSize; i++) {
            available = input.available();
            if (available == 0 || available == -1)
                break;

            byte[] chunk = new byte[CHUNK_SIZE + 1]; // +1 for channel header
            chunk[0] = channel; // set the channel header

            input.read(chunk, 1, CHUNK_SIZE);

            chunks[i] = chunk;
        }
        Log.d("KRSystem", "Chunks: " + Arrays.deepToString(chunks));
        return chunks;
    }
}
