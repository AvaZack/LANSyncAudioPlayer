package com.ava.zack.lansyncaudioplayer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.Nullable;

public class MainService extends Service {

  private AudioClient mClient;
  private AudioServer mServer;
  private boolean isServer = false;

  @Nullable @Override public IBinder onBind(Intent intent) {
    return null;
  }

  @Override public void onCreate() {
    //check settings
    if (isServer()) {
      mServer = new AudioServer(this);
      mServer.start();
    }
    mClient = new AudioClient(this);
    mClient.start(isServer);
  }

  private boolean isServer() {
    isServer = Settings.Global.getInt(getContentResolver(), Config.SETTINGS_IS_SERVER, 0) == 1;
    return isServer;
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {

    if (intent == null || intent.getAction() == null) {
      return super.onStartCommand(intent, flags, startId);
    }

    //if (Config.) {
    //
    //} else {
    //
    //}

    return super.onStartCommand(intent, flags, startId);
  }
}
