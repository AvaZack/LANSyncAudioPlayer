package com.ava.zack.lansyncaudioplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import com.ava.zack.lansyncaudioplayer.network.udp.UDPMessenger;
import com.ava.zack.lansyncaudioplayer.playback.DecoderCallback;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static com.ava.zack.lansyncaudioplayer.network.udp.Message.MSG_SERVER_JOIN_BROADCAST_MSG;

public class AudioServer implements DecoderCallback {

  private static final String TAG = "AudioServer";
  private static boolean DBG = true;

  private final int BUFFER_SIZE = 256;
  private final int INTERVAL_BROADCAST_MILLIS = 5 * 1000;

  private static final String MESSAGE_BROADCAST_DISCOVERY = "server.msg.broadcast.discovery";

  private boolean isBroadcasting = false;
  private boolean isAcceptingClients = false;

  private Context mContext;

  private ServerSocket mServerSocket;

  private ArrayList<ClientInfo> mClients;
  private ArrayList<ClientInfo> mDeadClients;

  private StreamAudioDecoder mAudioDecoder;
  private UDPMessenger mUdpMessenger;

  //should be singleton.
  AudioServer(Context context) {
    mContext = context;
    mClients = new ArrayList<>(3);
    mDeadClients = new ArrayList<>();
    mAudioDecoder = new StreamAudioDecoder(this);
    mUdpMessenger = new UDPMessenger(context, -1) {
      @Override public void onIncomingMessage() {
        super.onIncomingMessage();
      }
    };
  }

  public void start() {
    //start up device discovery thread
    startBroadcast();
    startAcceptingClients();
    startDecoding();
  }

  private void startAcceptingClients() {

    new Thread() {
      @Override
      public void run() {
        try {
          mServerSocket = new ServerSocket(Config.TCP_DATA_TRANSMITTING_PORT);
        } catch (IOException e) {
          e.printStackTrace();
        }
        isAcceptingClients = true;
        while (isAcceptingClients) {
          try {
            Log.d(TAG, "wait for client..");
            Socket socket = mServerSocket.accept();
            Log.d(TAG, "new client connected, ip:" + socket.getInetAddress());
            addClient(new ClientInfo(socket, socket.getInetAddress(), socket.getPort(),
                mClients.size() + 1));
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }.start();
  }

  //start udp broadcast for device discovery
  private void startBroadcast() {
    isBroadcasting = true;

    new Thread() {
      @Override public void run() {
        while (isBroadcasting) {
          if (DBG) {
            Log.d(TAG, "udp device discovery broadcasting");
          }
          try {
            mUdpMessenger.broadcast(MSG_SERVER_JOIN_BROADCAST_MSG,
                InetAddress.getByName(Config.UDP_BROADCAST_IP_ADDR_STR));
          } catch (IOException e) {
            e.printStackTrace();
          }
          try {
            Thread.sleep(INTERVAL_BROADCAST_MILLIS);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }.start();
  }

  private void stopBroadcast() {
    if (DBG) {
      Log.d(TAG, "stopBroadcast");
    }
    isBroadcasting = false;
  }

  private void stopAcceptingClients() {
    if (DBG) {
      Log.d(TAG, "stop accepting.");
    }
    isAcceptingClients = false;
  }

  private void stopDecoding() {
    if (DBG) {
      Log.d(TAG, "stop dispatching.");
    }
  }

  private void startDecoding() {
    mAudioDecoder.start();
  }

  public void stop() {
    stopBroadcast();
    stopAcceptingClients();
    stopDecoding();
  }

  //not used
/*  @Override public void onAudioTrackConfigUpdate(int sampleRate) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4);
    byte[] sampleRateBytes = byteBuffer.putInt(sampleRate).array();

    byte[] data = new byte[5];
    data[0] =
        Config.FLAG_CONFIG_AUDIO_TRACK; // flag -1 indicates the following data is used for audioTrack config.
    System.arraycopy(sampleRateBytes, 0, data, 1, sampleRateBytes.length);

    for (ClientInfo client : mClients) {
      try {
        byte[] reply = dispatchData(client, data, true);
        if (reply != null) {
          //check if client's audioTrack is ready
          if (reply[0] == 1) {
            Log.i(TAG, "client " + client.ip + " is ready");
          } else {
            Log.i(TAG, "client " + client.ip + " is NOT!! ready");
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
        mDeadClients.add(client);
      }
    }
    if (!mDeadClients.isEmpty()) {
      if (DBG) {
        Log.d(TAG, "dead=" + mDeadClients.size());
      }
      //close dead sockets
      for (ClientInfo deadClient : mDeadClients) {
        try {
          deadClient.socket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      mClients.removeAll(mDeadClients);
      mDeadClients.clear();
    }
  }*/

  @Override public synchronized void onChuckReleased(int length, byte[] rawData) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4);
    byte[] lengthBytes = byteBuffer.putInt(length).array();

    byte[] data = new byte[4 + rawData.length];
    //data[0] = Config.FLAG_RAW_AUDIO_DATA; // flag -2 indicates the following data is raw audio data.
    System.arraycopy(lengthBytes, 0, data, 0, lengthBytes.length);
    System.arraycopy(rawData, 0, data, 4, rawData.length);

    for (ClientInfo client : mClients) {
      try {
        dispatchData(client, data, false);
      } catch (IOException e) {
        e.printStackTrace();
        mDeadClients.add(client);
      }
    }
    if (!mDeadClients.isEmpty()) {
      if (DBG) {
        Log.d(TAG, "dead=" + mDeadClients.size());
      }
      //close dead sockets
      for (ClientInfo deadClient : mDeadClients) {
        try {
          deadClient.socket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      mClients.removeAll(mDeadClients);
      mDeadClients.clear();
    }
  }

  private byte[] dispatchData(ClientInfo client, byte[] data, boolean needReply)
      throws IOException {

    OutputStream os = client.socket.getOutputStream();
    os.write(data);

    if (needReply) {
      InputStream is = client.socket.getInputStream();
      byte[] replyData = new byte[8];
      int size = 0;
      if ((size = is.read(replyData)) > 0) {
        Log.d(TAG, "client reply size=" + size);
        return replyData;
      }
    }

    return null;
  }

  private class ClientInfo {
    Socket socket;
    InetAddress ip;
    int port;
    int channel;

    ClientInfo(Socket socket, InetAddress ip, int port, int channel) {
      this.socket = socket;
      this.ip = ip;
      this.port = port;
      this.channel = channel;
    }
  }

  private synchronized void addClient(ClientInfo info) {
    //do not add this client until it has been ready.
    //
    //onAudioTrackConfigUpdate(mAudioDecoder.getSampleRate());
    if (DBG) {
      Log.d(TAG, "Thread=" + Thread.currentThread().getName());
    }

    //add client, MUST sync
    boolean succeeded = initClient(info, mAudioDecoder.getSampleRate());

    if (succeeded) {
      Log.d(TAG, "add client=" + info.ip);
      mClients.add(info);
    } else {
      Log.d(TAG, "failed to init clients, do not add.");
    }
    //checkClients();
  }

  private boolean initClient(ClientInfo client, int sampleRate) {

    byte[] configData = new byte[8];
    ByteBuffer byteBuffer = ByteBuffer.allocate(4);
    byte[] sampleRateBytes = byteBuffer.putInt(sampleRate).array();
    ByteBuffer channelByteBuffer = ByteBuffer.allocate(4);
    byte[] channelRateBytes = channelByteBuffer.putInt(client.channel).array();
    //byte[] data = new byte[4];
    //data[0] = Config.FLAG_CONFIG_AUDIO_TRACK; // flag -1 indicates the following data is used for audioTrack config.
    System.arraycopy(sampleRateBytes, 0, configData, 0, sampleRateBytes.length);
    System.arraycopy(channelRateBytes, 0, configData, 4, channelRateBytes.length);

    try {
      byte[] reply = dispatchData(client, configData, true);
      if (reply != null) {
        //check if client's audioTrack is ready
        if (reply[0] == 1) {
          Log.i(TAG, "client " + client.ip + " is ready");
        } else {
          Log.i(TAG, "client " + client.ip + " is NOT!! ready");
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private void addClient(Socket socket, InetAddress inetAddress, int port, int channel) {

  }

  class StreamAudioDecoder {

    private static final String TAG = "AudioServer.Decoder";
    private static final boolean DBG = true;

    public static final String DEFAULT_ONLINE_STREAM_AUDIO_URL =
        "http://other.web.ri01.sycdn.kuwo.cn/resource/n3/40/17/494834159.mp3";
    private long DEQUEUE_BUFFER_TIMEOUT_USEC = 100000L;

    private boolean isDecoding = false;
    private boolean mLoop = true;

    private DecoderCallback mDecodeCallback;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    public StreamAudioDecoder(DecoderCallback callback) {
      mDecodeCallback = callback;
    }

    private static final int START_DELAY_MILLIS = 10 * 1000;

    private static final int DEFAULT_SAMPLE_RATE = 44100;

    private int mSampleRate = DEFAULT_SAMPLE_RATE;

    public int getSampleRate() {
      return mSampleRate;
    }

    public void start() {
      new Thread() {
        @Override public void run() {

          if (DBG) {
            Log.d(TAG, "stream resource decoding started..");
          }
          isDecoding = true;
          MediaExtractor extractor = null;
          MediaCodec decoder = null;
          try {
            extractor = new MediaExtractor();
            extractor.setDataSource(DEFAULT_ONLINE_STREAM_AUDIO_URL);
            int audioTrackIndex = selectAudioTrack(extractor);
            extractor.selectTrack(audioTrackIndex);
            MediaFormat format = extractor.getTrackFormat(audioTrackIndex);

            //synchronize audio SAMPLE RATE to clients so that they can configure AudioTrack correctly.
            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();

            //wait for clients ready
            try {
              Thread.sleep(START_DELAY_MILLIS);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }

            decode(decoder, extractor);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }.start();
    }

    public void setLoop(boolean loop) {
      mLoop = loop;
    }

    public boolean isLoop() {
      return mLoop;
    }

    private void decode(MediaCodec decoder, MediaExtractor extractor) {
      boolean outputDone = false;
      boolean inputDone = false;

      while (!outputDone) {
        if (!inputDone) {
          int inputBufferIndex = decoder.dequeueInputBuffer(DEQUEUE_BUFFER_TIMEOUT_USEC);
          if (inputBufferIndex > 0) {
            Log.d(TAG, "dequeueInput index=" + inputBufferIndex);
            ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
            if (inputBuffer != null) {
              int chunkSize = extractor.readSampleData(inputBuffer, 0);
              Log.d(TAG, "sample size=" + chunkSize);
              if (chunkSize < 0) {
                if (DBG) {
                  Log.d(TAG, "input reached EOS, install a mark for output.");
                }
                //input End of stream
                if (mLoop) {
                  Log.d(TAG, "Reached EOS, looping");
                  extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                  chunkSize = extractor.readSampleData(inputBuffer, 0);
                  inputDone = false;
                } else {
                  outputDone = true;
                }

                //decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0,
                //    Config.MY_BUFFER_FLAG_END_OF_STREAM);
                //inputDone = true;
              }
              long presentationTimeUs = extractor.getSampleTime();
              decoder.queueInputBuffer(inputBufferIndex, 0, chunkSize, presentationTimeUs, 0);
              extractor.advance();
            }
          }
        }

        if (!outputDone) {
          int decoderStatusOrIndex =
              decoder.dequeueOutputBuffer(mBufferInfo, DEQUEUE_BUFFER_TIMEOUT_USEC);
          if (DBG) {
            Log.d(TAG, "outputBuffer index=" + decoderStatusOrIndex);
          }
          if (decoderStatusOrIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            // no output available yet
            if (DBG) Log.d(TAG, "no output from decoder available");
          } else if (decoderStatusOrIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = decoder.getOutputFormat();
            if (DBG) Log.d(TAG, "decoder output format changed: " + newFormat);
          } else if (decoderStatusOrIndex < 0) {
            throw new RuntimeException(
                "unexpected result from decoder.dequeueOutputBuffer: " +
                    decoderStatusOrIndex);
          } else { // decoderStatusOrIndex >= 0
            ByteBuffer outputBuffer = decoder.getOutputBuffer(decoderStatusOrIndex);
            if (outputBuffer != null) {
              final byte[] chunk = new byte[mBufferInfo.size];
              if (DBG) {
                Log.d(TAG, "chunkSize=" + chunk.length);
              }
              outputBuffer.get(chunk);
              outputBuffer.clear();

              mDecodeCallback.onChuckReleased(chunk.length, chunk);
              decoder.releaseOutputBuffer(decoderStatusOrIndex, false);

              //EOS strategy
              //if ((mBufferInfo.flags & Config.MY_BUFFER_FLAG_END_OF_STREAM) != 0) {
              //  if (DBG) Log.d(TAG, "output EOS");
              //  if (mLoop) {
              //    Log.d(TAG, "Reached EOS, looping");
              //    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
              //    inputDone = false;
              //  } else {
              //    outputDone = true;
              //  }
              //}
            }
          }
        }
      }
    }

    public void setDecodeCallback(DecoderCallback callback) {
      mDecodeCallback = callback;
    }

    public boolean isDecoding() {
      return isDecoding;
    }

    private int selectAudioTrack(MediaExtractor extractor) {
      // Select the first video track we find, ignore the rest.
      int numTracks = extractor.getTrackCount();
      for (int i = 0; i < numTracks; i++) {
        MediaFormat format = extractor.getTrackFormat(i);
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (mime.startsWith("audio/")) {
          if (DBG) {
            Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
          }
          return i;
        }
      }

      return -1;
    }
  }
}
