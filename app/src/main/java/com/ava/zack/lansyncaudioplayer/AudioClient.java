package com.ava.zack.lansyncaudioplayer;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import com.ava.zack.lansyncaudioplayer.network.udp.Message;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class AudioClient {

  private static final String TAG = "AudioClient";
  private final boolean DBG = true;

  private final int BUFFER_SIZE = 5120;

  private Context mContext;

  public AudioClient(Context context) {
    mContext = context;
    mPlayer = new StreamAudioPlayer();
  }

  private static AudioClient mInstance;

  public static AudioClient getInstance(Context context) {
    if (mInstance == null) {
      mInstance = new AudioClient(context);
    }
    return mInstance;
  }

  private boolean isRunning = false;
  private Thread mServerScanThread;
  private Socket mTcpSocket;

  private StreamAudioPlayer mPlayer;

  public void stop() {

  }

  public void start(final boolean internal) {

    Runnable runnable = new Runnable() {
      @Override public void run() {

        if (DBG) {
          Log.d(TAG, "run server scanning . [");
        }
        DatagramSocket socket = null;

        while (isRunning) {

          if (internal) {
            Log.d(TAG, "internal client in server started. just join directly.");
            try {
              join(InetAddress.getLocalHost(), 0);
            } catch (UnknownHostException e) {
              e.printStackTrace();
            }
          } else {
            byte[] buffer = new byte[21];
            DatagramPacket rPacket = new DatagramPacket(buffer, buffer.length);
            try {
              socket = new DatagramSocket(Config.DEVICE_DISCOVERY_UDP_PORT);
            } catch (IOException e) {
              Log.e(TAG,
                  "Impossible to create a new MulticastSocket on port "
                      + Config.DEVICE_DISCOVERY_UDP_PORT, e);
              return;
            }
            try {
              //block here..
              Log.d(TAG, "waiting for udp broadcast...");
              socket.receive(rPacket);

              if (DBG) {
                Log.d(TAG, "run. [rSocket getInetAddress=" + socket.getInetAddress()
                    + ", getLocalAddress" + socket.getLocalAddress()
                    + ", getLocalSocketAddress=" + socket.getLocalSocketAddress()
                    + ", getRemoteSocketAddress=" + socket.getRemoteSocketAddress()
                );
              }
            } catch (IOException e1) {
              Log.e(TAG, "There was a problem receiving the incoming message.", e1);
            }

            byte data[] = rPacket.getData();
            if (DBG) {
              Log.d(TAG, "rPacket data=" + new String(rPacket.getData())
                  + ", address" + rPacket.getAddress()
                  + ", getSocketAddress=" + rPacket.getSocketAddress()
                  + ", getPort=" + rPacket.getPort()
              );
            }
            String messageText = "";
            try {
              messageText = new String(data, 0, data.length, "UTF-8");
            } catch (UnsupportedEncodingException e) {
              Log.e(TAG, "UTF-8 encoding is not supported. Can't receive the incoming message.", e);
            }

            //process UDP msgs
            if (Message.MSG_SERVER_JOIN_BROADCAST_MSG.equals(messageText)) {
              //reading loop block here
              join(rPacket.getAddress(), rPacket.getPort());
            }
          }
        }
        if (socket != null) {
          socket.close();
        }
      }
    };

    mServerScanThread = new Thread(runnable);
    isRunning = true;
    mServerScanThread.start();
  }

  private void stopServerScanning() {
    isRunning = false;
  }

  //private void stopServer
  //
  private void join(final InetAddress address, int port) {
    try {
      Log.d(TAG, "connect server ++ block here?");
      mTcpSocket = new Socket(address, Config.TCP_DATA_TRANSMITTING_PORT);
      //Log.d(TAG, "connect server --");
      Log.d(TAG, "tcp connection established..");
      //
      startReceivingAudioData();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private boolean isPlaying = false;

  private void startReceivingAudioData() {
    //byte[] singleByte = new byte[1];
    byte[] fourBytes = new byte[4]; // read a integer
    try {
      InputStream is = mTcpSocket.getInputStream();
      OutputStream os = mTcpSocket.getOutputStream();
      int size;

      //waiting for init config data
      if (DBG) {
        Log.d(TAG, "config audioTrack msg received");
      }
      //read AudioTrack config msg..
      if ((size = is.read(fourBytes)) == 4) {
        int sampleRate = ByteBuffer.wrap(fourBytes).getInt();
        Log.d(TAG, "config audioTrack, sampleRate=" + sampleRate);
        int channel = 0;
        if((size = is.read(fourBytes)) == 4){
          channel = ByteBuffer.wrap(fourBytes).getInt();
        }
        //synchronous init
        mPlayer.init(sampleRate, channel);
        os.write(new byte[] { mPlayer.isReady() ? (byte) 1 : (byte) 0 });
      } else {
        Log.d(TAG, "getting sampleRate expected 4 bytes, read=" + size);
      }

      if (!mPlayer.isReady()) {
        Log.d(TAG, "failed to init player, exit.");
        return;
      }

      //read raw data
      isPlaying = true;
      byte[] rawDataBuffer = new byte[BUFFER_SIZE];
      Log.d(TAG, "reading tcp data loop started");
      while (isPlaying) {
        if (DBG) {
          Log.d(TAG, "going to receive raw audio data");
        }
        //raw audio data
        if ((size = is.read(fourBytes)) == 4) {
          int length = ByteBuffer.wrap(fourBytes).getInt();
          Log.d(TAG, "length we read in client=" + length);

          //FIXME maybe expected data is not available?
          //should we sleep a little while to wait?
          Thread.sleep(20);

          if ((size = is.read(rawDataBuffer, 0, length)) == length) {
            mPlayer.output(rawDataBuffer, 0, length);
          } else {
            Log.d(TAG, "getting raw data, expected=" + length + ", read=" + size);
          }
        } else {
          Log.d(TAG, "getting chunk size, expected 4 bytes, read=" + size);
        }
      }

      //byte[] data = new byte[BUFFER_SIZE];
      //int size;
      //while ((size = is.read(data)) > 0) {
      //  Log.d(TAG, "read bytes:" + size);
      //  RawDataPlayer.getInstance().write(data, 0, size);
      //}
    } catch (IOException e) {
      Log.e(TAG, "receiving socket broken, restart device scanning", e);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  class StreamAudioPlayer {
    int DEFAULT_SAMPLE_RATE = 44100;

    final int[] legalSampleRate = new int[] {
        11025, 22050, 32000, 44100, 47250, 48000, 50000, 96000
    };

    AudioTrack mAudioTrack;

    private boolean isReady = false;
    private int mAudioTrackChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;

    boolean isReady() {
      return isReady;
    }

    void init(int sampleRate, int channel) {
      if (isReady) {
        //this includes stop();
        mAudioTrack.release();
        Log.d(TAG, "reconfigure out audioTrack");
      }
      if (Arrays.binarySearch(legalSampleRate, sampleRate) < 0) {
        Log.e(TAG, "illegal sampleRate=" + sampleRate, new IllegalArgumentException());
        return;
      }

      if (DBG) {
        Log.d(TAG, "channel config=" + mAudioTrackChannelConfig);
      }

      int bufferSize =
          AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO,
              AudioFormat.ENCODING_PCM_16BIT);
      mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
          mAudioTrackChannelConfig,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize,
          AudioTrack.MODE_STREAM);
      if (channel == 1) {
        mAudioTrack.setStereoVolume(1.0f, 0.0f);
      } else if (channel == 2) {
        mAudioTrack.setStereoVolume(0.0f, 1.0f);
      }
      mAudioTrack.play();
      isReady = true;
    }

    public void output(byte[] audioData, int offsetInBytes, int sizeInBytes) {
      mAudioTrack.write(audioData, offsetInBytes, sizeInBytes);
    }
  }
}
