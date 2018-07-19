package com.ava.zack.lansyncaudioplayer;

import android.database.ContentObserver;
import android.os.Handler;

public class ConfigObserver extends ContentObserver {
  /**
   * Creates a content observer.
   *
   * @param handler The handler to run {@link #onChange} on, or null if none.
   */
  public ConfigObserver(Handler handler) {
    super(handler);
  }
}
