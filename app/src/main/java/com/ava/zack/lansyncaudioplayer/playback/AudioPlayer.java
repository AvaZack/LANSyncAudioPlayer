/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ava.zack.lansyncaudioplayer.playback;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;

public class AudioPlayer {
  private static final String TAG = "AudioPlayer";
  private static final boolean VERBOSE = false;

  // Declare this here to reduce allocations.
  private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

  // May be set/read by different threads.
  private volatile boolean mIsStopRequested;

  private File mSourceFile;
  private String mSourceUrl;
  private static boolean isLocalResource = false;
  //SpeedControlCallback mFrameCallback;
  private boolean mLoop;

  public static final String PATH_DEFAULT_AUDIO =
      Environment.getExternalStorageDirectory().getAbsolutePath() + "/syncAudio";

  public static final String DEFAULT_ONLINE_STREAM_AUDIO_URL =
      "http://other.web.ra01.sycdn.kuwo.cn/67e4fc4dcb11dfb3e9dba67ba7e4ed02/5b4c42b3/resource/n2/192/80/56/563491399.mp3?bdfrom=feixun";

  public AudioPlayer(File sourceFile)
      throws IOException {
    mSourceFile = sourceFile;
    //mFrameCallback = frameCallback;

  }

  public AudioPlayer() {
    mSourceFile = new File(PATH_DEFAULT_AUDIO);
    mSourceUrl = DEFAULT_ONLINE_STREAM_AUDIO_URL;
  }

  public void setLoopMode(boolean loopMode) {
    mLoop = loopMode;
  }

  public void requestStop() {
    mIsStopRequested = true;
  }

  private int mSampleRate = 0;
  private int mChannelCount = 0;
  private String mMime = "";

  public void play() throws IOException {
    MediaExtractor extractor = null;
    MediaCodec decoder = null;
    AudioTrack audioTrack = null;
    // The MediaExtractor error messages aren't very useful.  Check to see if the input
    // file exists so we can throw a better one if it's not there.
    //if (!mSourceFile.canRead()) {
    //  throw new FileNotFoundException("Unable to read " + mSourceFile);
    //}

    try {
      extractor = new MediaExtractor();
      if (isLocalResource) {
        extractor.setDataSource(mSourceFile.toString());
      } else {
        extractor.setDataSource(mSourceUrl);
      }

      //Just to retrieve the duration; TODO
      //            int videoTrackIndex = selectVideoTrack(extractor);
      //            if (videoTrackIndex < 0) {
      //                throw new RuntimeException("No video track found in " + mSourceFile);
      //            }
      //            MediaFormat videoTrackFormat = extractor.getTrackFormat(videoTrackIndex);
      //            mDuration = videoTrackFormat.getLong(MediaFormat.KEY_DURATION);

      int audioTrackIndex = selectAudioTrack(extractor);

      if (audioTrackIndex < 0) {
        throw new RuntimeException("No audio track found in " + mSourceFile);
      }
      if (VERBOSE) {
        Log.d(TAG, "duration:" + mDuration);
      }

      extractor.selectTrack(audioTrackIndex);
      MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
      //            mDuration = format.getLong(MediaFormat.KEY_DURATION);

      mMime = format.getString(MediaFormat.KEY_MIME);
      mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      mChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      if (VERBOSE) {
        Log.d(TAG,
            "mime=" + mMime + ", frameRate=" + mSampleRate + ", channelCount=" + mChannelCount);
      }

      //            MediaFormat mediaFormatSpecified = specifyAudioFormat();
      //            decoder = MediaCodec.createDecoderByType("audio/mp4a-latm");

      decoder = MediaCodec.createDecoderByType(mMime);
      decoder.configure(format, null, null, 0);
      decoder.start();

      int bufferSize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO,
          AudioFormat.ENCODING_PCM_16BIT);
      audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
          AudioFormat.CHANNEL_OUT_STEREO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize,
          AudioTrack.MODE_STREAM);
      audioTrack.play();

      doExtract(extractor, audioTrackIndex, decoder, audioTrack);
    } finally {
      // release everything we grabbed
      if (decoder != null) {
        decoder.stop();
        decoder.release();
        decoder = null;
      }
      if (extractor != null) {
        extractor.release();
        extractor = null;
      }
      if (audioTrack != null) {
        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
      }
    }
  }

  public void stopPlayback() {

  }

  private long mDuration = 0;

  /**
   * Selects the video track, if any.
   *
   * @return the track index, or -1 if no video track is found.
   */
  private int selectAudioTrack(MediaExtractor extractor) {
    // Select the first video track we find, ignore the rest.
    int numTracks = extractor.getTrackCount();
    for (int i = 0; i < numTracks; i++) {
      MediaFormat format = extractor.getTrackFormat(i);
      String mime = format.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("audio/")) {
        if (VERBOSE) {
          Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
        }
        return i;
      }
    }

    return -1;
  }

  private MediaFormat specifyAudioFormat() {
    MediaFormat mediaFormat = new MediaFormat();
    //        mediaFormat.setString(MediaFormat.KEY_MIME, mMime);
    mediaFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
    mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, mSampleRate);
    mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mChannelCount);

    int audioProfile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;

    int samplingFreq[] = {
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
        16000, 12000, 11025, 8000
    };

    // Search the Sampling Frequencies
    int sampleIndex = -1;
    for (int i = 0; i < samplingFreq.length; ++i) {
      if (samplingFreq[i] == mSampleRate) {
        Log.d("TAG", "kSamplingFreq " + samplingFreq[i] + " i : " + i);
        sampleIndex = i;
      }
    }

    if (sampleIndex == -1) {
      return null;
    }

    //Does it
    ByteBuffer csd = ByteBuffer.allocate(2);
    csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));

    csd.position(1);
    csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (mChannelCount << 3)));
    csd.flip();
    mediaFormat.setByteBuffer("csd-0", csd); // add csd-0

    for (int k = 0; k < csd.capacity(); ++k) {
      Log.e("TAG", "csd : " + csd.array()[k]);
    }

    return mediaFormat;
  }

  private int selectVideoTrack(MediaExtractor extractor) {
    // Select the first video track we find, ignore the rest.
    int numTracks = extractor.getTrackCount();
    for (int i = 0; i < numTracks; i++) {
      MediaFormat format = extractor.getTrackFormat(i);
      String mime = format.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("video/")) {

        if (VERBOSE) {
          Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
        }
        return i;
      }
    }

    return -1;
  }

  /**
   * Work loop.  We execute here until we run out of video or are told to stop.
   */
  private void doExtract(MediaExtractor extractor, int trackIndex, MediaCodec decoder,
      AudioTrack audioTrack) {

    final int TIMEOUT_USEC = 100000;
    ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
    ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();
    int inputChunk = 0;
    long firstInputTimeNsec = -1;

    boolean outputDone = false;
    boolean inputDone = false;

    long lastNano = 0;

    while (!outputDone) {
      if (mIsStopRequested) {
        Log.d(TAG, "Stop requested");
        return;
      }

      // Feed more data to the decoder.
      if (!inputDone) {
        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufIndex >= 0) {
          if (firstInputTimeNsec == -1) {
            firstInputTimeNsec = System.nanoTime();
          }
          ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
          // Read the sample data into the ByteBuffer.  This neither respects nor
          // updates inputBuf's position, limit, etc.
          int chunkSize = extractor.readSampleData(inputBuf, 0);
          //

          if (chunkSize < 0) {
            if (mLoop) {
              Log.e(TAG, "chunkSize is 0, seekTo the start of the video.");

              Log.d(TAG, "Notify to loopReset . Thread=" + Thread.currentThread().getName());

              if (VERBOSE) {
                Log.d(TAG, "sampleTime seek to 0 :" + extractor.getSampleTime());
              }

              try {
                Thread.sleep(10000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              // Use BUFFER_FLAG_CODEC_CONFIG instead.
              extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
              chunkSize = extractor.readSampleData(inputBuf, 0);

              //                            audioTrack.flush();
              //TODO
              // Do the seeking ,if needed, at the first loop ends
              // to avoid abnormal playing at the video beginning.

            } else {
              // End of stream -- send empty frame with EOS flag set.
              decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                  MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputDone = true;
              if (VERBOSE) Log.d(TAG, "sent input EOS");
            }
          }

          if (extractor.getSampleTrackIndex() != trackIndex) {
            Log.w(TAG, "WEIRD: got sample from track " +
                extractor.getSampleTrackIndex() + ", expected " + trackIndex);
          }
          long presentationTimeUs = extractor.getSampleTime();
          if (VERBOSE) {
            Log.d(TAG, "sampleFlag:" + extractor.getSampleFlags());
          }

          decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
              presentationTimeUs, 0 /*flags*/);
          if (VERBOSE) {
            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                chunkSize);
          }
          inputChunk++;//for log

          //FIXME
          extractor.advance();
          //                    }
        } else {
          if (VERBOSE) Log.d(TAG, "input buffer not available");
        }
      }

      if (!outputDone) {
        int decoderStatusOrIndex = decoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        if (decoderStatusOrIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
          // no output available yet
          if (VERBOSE) Log.d(TAG, "no output from decoder available");
        } else if (decoderStatusOrIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
          // not important for us, since we're using Surface
          decoderOutputBuffers = decoder.getOutputBuffers();
          if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
        } else if (decoderStatusOrIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat newFormat = decoder.getOutputFormat();
          if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
          audioTrack.setPlaybackRate(newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        } else if (decoderStatusOrIndex < 0) {
          throw new RuntimeException(
              "unexpected result from decoder.dequeueOutputBuffer: " +
                  decoderStatusOrIndex);
        } else { // decoderStatusOrIndex >= 0
          if (firstInputTimeNsec != 0) {
            // Log the delay from the first buffer of input to the first buffer
            // of output.
            long nowNsec;
            nowNsec = System.nanoTime();

            if (VERBOSE) {
              Log.d(TAG, "startup lag " + ((nowNsec - firstInputTimeNsec) / 1000000.0) + " ms");
            }
            firstInputTimeNsec = 0;

            if (VERBOSE) {
              Log.d(TAG, "first presentation Us:" + mBufferInfo.presentationTimeUs);
            }
          }

          //                    boolean doRender = (mBufferInfo.size != 0);
          //                    boolean doRender = true;
          //                    if (doRender && mFrameCallback != null) {
          //                        mFrameCallback.preRender(mBufferInfo.presentationTimeUs, mDuration, extractor);
          //                    }

          ByteBuffer outBuffer = decoderOutputBuffers[decoderStatusOrIndex];

          final byte[] chunk = new byte[mBufferInfo.size];
          outBuffer.get(chunk); // Read the buffer all at once
          outBuffer.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN

          audioTrack.write(chunk, mBufferInfo.offset,
              mBufferInfo.offset + mBufferInfo.size); // AudioTrack write data
          decoder.releaseOutputBuffer(decoderStatusOrIndex, false);
          if (VERBOSE) {
            Log.d(TAG, "presentation Us=" + mBufferInfo.presentationTimeUs);
          }

          //if (doRender && mFrameCallback != null) {
          //    mFrameCallback.postRender();
          //}

          boolean doLoop = false;

          //FLAG_END_OF_STREAM is not available on RockChip platform.
          if ((mBufferInfo.flags & BUFFER_FLAG_CODEC_CONFIG) != 0) {
            if (VERBOSE) Log.d(TAG, "output EOS");
            if (mLoop) {
              doLoop = true;
            } else {
              outputDone = true;
            }
          }

          if (doLoop) {
            Log.d(TAG, "Reached EOS, looping");
            //int decoderStatusOrIndex = decoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            if (VERBOSE) {
              Log.d(TAG, "sampleTime after EOS:" + extractor.getSampleTime());
            }

            inputDone = false;
            //                        decoder.flush();    // reset decoder state
            //                        mFrameCallback.loopReset();
          }
        }
      }
    }
  }



  public static class AudioPlayTask implements Runnable {
    private static final int MSG_PLAY_STOPPED = 0;

    private AudioPlayer mAudioPlayer;
    private MoviePlayer.PlayerFeedback mFeedback;
    private boolean mDoLoop;
    private Thread mThread;
    private LocalHandler mLocalHandler;

    private final Object mStopLock = new Object();
    private boolean mStopped = false;

    /**
     * Prepares new AudioPlayTask.
     *
     * @param videoPlayer The player object, configured with control and output.
     * @param feedback UI feedback object.
     */
    public AudioPlayTask(AudioPlayer videoPlayer, MoviePlayer.PlayerFeedback feedback) {
      mAudioPlayer = videoPlayer;
      mFeedback = feedback;

      mLocalHandler = new LocalHandler();
    }

    /**
     * Sets the loop mode.  If true, playback will loop forever.
     */
    public void setLoopMode(boolean loopMode) {
      mDoLoop = loopMode;
    }

    /**
     * Creates a new thread, and starts execution of the player.
     */
    public void execute() {
      mAudioPlayer.setLoopMode(mDoLoop);
      mThread = new Thread(this, "Audio Player");
      mThread.start();
    }

    /**
     * Requests that the player stop.
     * <p>
     * Called from arbitrary thread.
     */
    public void requestStop() {
      mAudioPlayer.requestStop();
    }

    /**
     * Wait for the player to stop.
     * <p>
     * Called from any thread other than the AudioPlayTask thread.
     */
    public void waitForStop() {
      synchronized (mStopLock) {
        while (!mStopped) {
          try {
            mStopLock.wait();
          } catch (InterruptedException ie) {
            // discard
          }
        }
      }
    }

    @Override
    public void run() {
      try {
        mAudioPlayer.play();
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      } finally {
        // tell anybody waiting on us that we're done
        synchronized (mStopLock) {
          mStopped = true;
          mStopLock.notifyAll();
        }

        // Send message through Handler so it runs on the right thread.
        mLocalHandler.sendMessage(
            mLocalHandler.obtainMessage(MSG_PLAY_STOPPED, mFeedback));
      }
    }

    private static class LocalHandler extends Handler {
      @Override
      public void handleMessage(Message msg) {
        int what = msg.what;

        switch (what) {
          case MSG_PLAY_STOPPED:
            MoviePlayer.PlayerFeedback fb = (MoviePlayer.PlayerFeedback) msg.obj;
            fb.playbackStopped();
            break;
          default:
            throw new RuntimeException("Unknown msg " + what);
        }
      }
    }
  }
}
