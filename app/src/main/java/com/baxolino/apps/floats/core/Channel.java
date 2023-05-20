package com.baxolino.apps.floats.core;

import java.util.Arrays;

public class Channel {

  public static final Channel KNOW_REQUEST_CHANNEL = new Channel((byte) 1);
  public static final Channel FILE_REQUEST_CHANNEL = new Channel((byte) 2);

  private final byte[] channel;

  public Channel(byte[] channel) {
    if (channel.length != Config.CHANNEL_SIZE)
      throw new IllegalArgumentException("Wrong channel size");
    this.channel = channel;
  }

  public Channel(byte b) {
    channel = new byte[Config.CHANNEL_SIZE];
    channel[0] = b;
  }

  public byte[] bytes() {
    return channel;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Channel channel1 = (Channel) o;
    return Arrays.equals(channel, channel1.channel);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(channel);
  }
}
