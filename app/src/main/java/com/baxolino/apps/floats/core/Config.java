package com.baxolino.apps.floats.core;

public class Config {
    public static final int SLOTS_ALLOCATION = 15;

    // 2^12 == 4096
    public static final int CHUNK_SIZE = 1 << 10;

    // 4 bytes contain channel Id
    public static final int CHANNEL_SIZE = 4;

    public static final int FILE_NAME_LENGTH_SEPARATOR = '/';
}
