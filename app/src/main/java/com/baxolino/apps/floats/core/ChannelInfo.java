package com.baxolino.apps.floats.core;

import java.util.Arrays;

public class ChannelInfo {

  public static final ChannelInfo FILE_REQUEST_CHANNEL_INFO = new ChannelInfo((byte) 1);

  private final byte[] channel;

  public ChannelInfo(byte[] channel) {
    if (channel.length != Config.CHANNEL_SIZE)
      throw new IllegalArgumentException("Wrong channel size");
    this.channel = channel;
  }

  public ChannelInfo(byte b) {
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
    ChannelInfo channelInfo1 = (ChannelInfo) o;
    return Arrays.equals(channel, channelInfo1.channel);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(channel);
  }
}
