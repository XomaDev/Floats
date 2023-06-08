package com.baxolino.apps.floats.core;

public class Info {

    // 2^12 == 4096
    public static final int CHUNK_SIZE = 1 << 12;

    // 4 bytes contain channel Id
    public static final int CHANNEL_SIZE = 4;

    // a buffer size of 1 mb
    public static final int BUFFER_SIZE = 1_000_000;

}
