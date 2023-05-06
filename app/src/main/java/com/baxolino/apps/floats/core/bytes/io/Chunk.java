package com.baxolino.apps.floats.core.bytes.io;

public class Chunk {

    private int index = 0;

    private final int len;
    private final byte[] bytes;

    public Chunk(byte[] bytes) {
        this.bytes = bytes;
        len = bytes.length;
    }

    public int available() {
        return len - index;
    }

    public int read() {
        if (available() == 0)
            return -1;
        return bytes[index++];
    }
}
