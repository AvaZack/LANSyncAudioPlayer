package com.ava.zack.lansyncaudioplayer;

public class Config {

  public static final int DEVICE_DISCOVERY_UDP_PORT = 10001;
  public static final int TCP_DATA_TRANSMITTING_PORT = 12321;

  public static final int MY_BUFFER_FLAG_END_OF_STREAM = 16;

  public static final byte FLAG_CONFIG_AUDIO_TRACK = -1;
  public static final byte FLAG_RAW_AUDIO_DATA = -2;
  public static final byte MSG_CLIENT_READY = -3;

  public final String INTENT_ACTION_CLIENT_CONFIG_CHANGED =
      "com.intent.action.client_config_changed";
  public final String INTENT_ACTION_SERVER_CONFIG_CHANGED =
      "com.intent.action.server_config_changed";

  public static final String SETTINGS_IS_SERVER = "lan.sync_playing.is_server";

  public static final String UDP_BROADCAST_IP_ADDR_STR = "255.255.255.255";
}
