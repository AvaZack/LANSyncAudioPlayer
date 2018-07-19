/*

UDP Messenger by Giacomo Furlan.
http://giacomofurlan.name

This software is being distributed under the Creative Common's Attribution 3.0 Unported licence (CC BY 3.0)
http://creativecommons.org/licenses/by/3.0/

You are not allowed to use this source code for pirate purposes.

This software is provided "as-is" and it comes with no warranties.

 */

package com.ava.zack.lansyncaudioplayer.network.udp;

import android.content.Context;
import android.util.Log;
import com.ava.zack.lansyncaudioplayer.Config;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.List;

public abstract class UDPMessenger {
  private static final boolean DBG = false;
  ;;
  private static String TAG = "UDPMessenger";
  protected static final Integer BUFFER_SIZE = 256;
  private static final int DEFAULT_PORT = 9191;

  protected int mPort;
  private boolean mReceiveMessage = false;

  protected Context mContext;

  private Message mIncomingMessage;
  private Thread mReceiverThread;

  /**
   * Class constructor
   *
   * @param context the application's context
   * @param tag a valid string, used to filter the UDP broadcast messages (in and out). It can't be null or 0-characters long.
   * @param port the port to multicast to. Must be between 1025 and 49151 (inclusive)
   * @param connectionPort the port to get the connection back. Must be between 1025 and 49151
   */
  public UDPMessenger(Context context, int port) throws IllegalArgumentException {
    this.mContext = context.getApplicationContext();
    if (port > 0) {
      mPort = port;
    } else {
      mPort = DEFAULT_PORT;
    }

    //        if (DBG) {
    //            try {
    //                getNetwork(InetAddress.getByName("192.168.43.199"));
    //            } catch (SocketException e) {
    //                e.printStackTrace();
    //            } catch (UnknownHostException e) {
    //                // TODO Auto-generated catch block
    //                e.printStackTrace();
    //            }
    //        }

  }

  public boolean sendMessage(String message, String ip, int port)
      throws IllegalArgumentException, UnsupportedEncodingException,
      UnknownHostException {
    if (message == null || message.length() == 0) {
      throw new IllegalArgumentException();
    }
    Message msg = new Message(message);
    byte data[] = msg.toString().getBytes("UTF-8");
    data[data.length - 1] = '\0';
    return sendMessage(InetAddress.getByName(ip), port, data);
  }

  public boolean sendMessage(InetAddress inetAddr, int port, byte[] data) {
    if (DBG) {
      Log.d(TAG,
          "sendMessage. [data size="
              + data.length
              + ", inetAddr="
              + inetAddr
              + ", port="
              + port);
    }

    // Create the send socket
    // if (mSocket == null) {
    // try {
    // mSocket = new DatagramSocket();
    // } catch (SocketException e) {
    // Log.e(TAG, "There was a problem creating the sending socket. Aborting.", e);
    // return false;
    // }
    // }

    // Build the packet
    DatagramPacket packet = new DatagramPacket(data, data.length, inetAddr, port);
    try {
      mRSocket.send(packet);
    } catch (IOException e) {
      Log.e(TAG, "There was an error sending the UDP packet. Aborted.", e);
      return false;
    }

    return true;
  }

  private DatagramSocket mRSocket;

  public void startMessageReceiver() {
    Runnable receiver = new Runnable() {

      @Override
      public void run() {
        if (DBG) {
          Log.d(TAG, "run. [");
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket rPacket = new DatagramPacket(buffer, buffer.length);
        try {
          mRSocket = new DatagramSocket(mPort);
        } catch (IOException e) {
          Log.e(TAG, "Impossible to create a new MulticastSocket on port " + mPort, e);
          return;
        }

        while (mReceiveMessage) {
          Log.d(TAG, "run. [receiveMessages rPacket.getAddress=");
          try {
            mRSocket.receive(rPacket);

            if (DBG) {
              Log.d(TAG, "run. [rSocket getInetAddress=" + mRSocket.getInetAddress()
                  + ", getLocalAddress" + mRSocket.getLocalAddress()
                  + ", getLocalSocketAddress=" + mRSocket.getLocalSocketAddress()
                  + ", getRemoteSocketAddress=" + mRSocket.getRemoteSocketAddress()
              );
            }
          } catch (IOException e1) {
            Log.e(TAG, "There was a problem receiving the incoming message.", e1);
          }

          byte data[] = rPacket.getData();

          if (DBG) {
            Log.d(TAG, "run. [data=" + bytesToHex(data));
          }

          int i;
          for (i = 0; i < data.length; i++) {
            if (data[i] == '\0') {
              break;
            }
          }

          String messageText = "EMPTY-H";
          try {
            messageText = new String(data, 0, i, "UTF-8");
          } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "UTF-8 encoding is not supported. Can't receive the incoming message.", e);
          }

          try {
            // My ip is 192.168.1.2
            // run. [rPacket.getAddress()=/192.168.1.3, getSocketAddress=/192.168.1.3:52292
            if (DBG) {
              Log.d(TAG, "run. [rPacket.getAddress()=" + rPacket.getAddress()
                  + ", getSocketAddress=" + rPacket.getSocketAddress()
                  + ", rPacket.getPort()=" + rPacket.getPort());
            }

            setIncomingMessage(new Message(messageText, rPacket.getAddress(), rPacket.getPort()));
            onIncomingMessage();
          } catch (IllegalArgumentException ex) {
            Log.e(TAG, "There was a problem processing the message: " + messageText, ex);
          }

          Log.d(TAG, "End receive.incomingMessage=" + getIncomingMessage());
        }
        // ONLY once.
        mRSocket.close();
      }
    };

    mReceiveMessage = true;
    if (mReceiverThread == null) {
      mReceiverThread = new Thread(receiver);
    }

    if (!mReceiverThread.isAlive()) {
      mReceiverThread.start();
    }
  }

  private DatagramSocket mBroadcastSocket;

  public void broadcast(String broadcastMessage, InetAddress address) throws IOException {
    if (DBG) {
      Log.d(TAG, "udp broadcast, msg=" + broadcastMessage + ", dest=" + address);
    }
    if (mBroadcastSocket == null) {
      mBroadcastSocket = new DatagramSocket(Config.DEVICE_DISCOVERY_UDP_PORT);
    }
    mBroadcastSocket.setBroadcast(true);

    byte[] buffer = broadcastMessage.getBytes();

    DatagramPacket packet
        = new DatagramPacket(buffer, buffer.length, address, Config.DEVICE_DISCOVERY_UDP_PORT);
    mBroadcastSocket.send(packet);
    //mBroadcastSocket.close();
  }

  final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = hexArray[v >>> 4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }

  public void stopMessageReceiver() {
    mReceiveMessage = false;

    // Force stop.
    mReceiverThread.interrupt();
  }

  public void onIncomingMessage() {
    if (DBG) {
      Log.d(TAG, "onIncomingMessage. =" + getIncomingMessage());
    }
  }

  public Message getIncomingMessage() {
    return mIncomingMessage;
  }

  public void setIncomingMessage(Message incomingMessage) {
    mIncomingMessage = incomingMessage;
  }

  public void dumpNetworkinterfaces() throws SocketException {
    for (Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces();
        list.hasMoreElements(); ) {
      NetworkInterface netinterface = list.nextElement();
      Log.e("network_interfaces", "display name " + netinterface.getDisplayName());
      Log.d(TAG, " netinterface=" + netinterface);
      List<InterfaceAddress> interfaceAddresses = netinterface.getInterfaceAddresses();
      if (interfaceAddresses != null && !interfaceAddresses.isEmpty()) {
        for (InterfaceAddress ifa : interfaceAddresses) {
          if (DBG) {
            if ((ifa.getAddress() instanceof Inet4Address)) {

              byte[] address = ifa.getAddress().getAddress();

              // Register: FF 00 00 00
              // Bigendian: mem = FF 00 00 00. <mem low --> high>
              // byteToHex dumps : mem low -> high.
              int prefixNet = 0;
              short N = ifa.getNetworkPrefixLength();
              for (int i = 0; i < N; i++) {
                // Register.
                prefixNet |= (1 << (31 - i)); // Small
                // FF 00
              }

              // Default to bigendian.
              // Big-endian systems store the most significant byte of a word in the smallest address and
              // the least significant byte is stored in the largest address
              Log.d(TAG,
                  "prefixNet=" + bytesToHex(ByteBuffer.allocate(4).putInt(prefixNet).array()));

              // 01-01 00:25:35.890: D/UDPMessenger(1925): HEX=C0A82B01
              // Registor: 01 2B A8 C0. Mem: C0 A8 2B 01. /192.168.43.1
              Log.d(TAG, "HEX=" + bytesToHex(address));
              int subnetInt = prefixNet & ByteBuffer.wrap(address).getInt();
              Log.d(TAG, "subnet=" + bytesToHex(ByteBuffer.allocate(4).putInt(subnetInt).array()));
              Log.d(TAG, "is ipv4="
                  + (ifa.getAddress() instanceof Inet4Address)
                  + ", leng="
                  + address.length);
              Log.d(TAG,
                  "dumpNetworkinterfaces. [interfaceAddress=" + ifa + ", getPrefixLength=" + N
                      + ", getAddr=" + ifa.getAddress());
            }
          }
        }
      }

      // if ("wlan0".equals(netinterface.getDisplayName())) {
      // // ni = netinterface;
      // }
    }
  }
}
