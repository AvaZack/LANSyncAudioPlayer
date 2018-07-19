package com.ava.zack.lansyncaudioplayer.playback;

public interface DecoderCallback {
  //void onAudioTrackConfigUpdate(int sampleRate);

  void onChuckReleased(int length, byte[] data);
}
