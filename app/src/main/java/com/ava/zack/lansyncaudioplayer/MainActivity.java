package com.ava.zack.lansyncaudioplayer;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

public class MainActivity extends Activity {
  private AudioClient mClient;
  private AudioServer mServer;
  private final boolean isServer = false;

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    //check settings
    if (isServer) {
      mServer = new AudioServer(this);
      mServer.start();
    }
    mClient = new AudioClient(this);
    mClient.start(isServer);
  }
}
