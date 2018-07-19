package com.ava.zack.lansyncaudioplayer.playback;

import android.os.Environment;
import java.io.File;

public class AudioDecoder {

  private static final String DEFAULT_AUDIO_PATH =
      Environment.getExternalStorageDirectory() + "/syncAudio";

  private File mSourceFile;

  public AudioDecoder(File sourceFile) {
    mSourceFile = sourceFile;
  }

  public AudioDecoder() {
    mSourceFile = new File(DEFAULT_AUDIO_PATH);
  }
}
