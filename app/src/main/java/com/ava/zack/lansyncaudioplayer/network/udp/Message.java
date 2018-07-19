/*

UDP Messenger by Giacomo Furlan.
http://giacomofurlan.name

This software is being distributed under the Creative Common's Attribution 3.0 Unported licence (CC BY 3.0)
http://creativecommons.org/licenses/by/3.0/

You are not allowed to use this source code for pirate purposes.

This software is provided "as-is" and it comes with no warranties.

 */

package com.ava.zack.lansyncaudioplayer.network.udp;

import android.util.Log;
import java.net.InetAddress;

public class Message {
  private final static String TAG = "Message";
  private static final boolean DBG = false;
  private String message;
  private InetAddress ip;
  private int mSrcPort;

  public Message(String message) throws IllegalArgumentException {
    this(message, (InetAddress) null, 0);
  }

  public Message(String message, InetAddress ip, int srcPort) throws IllegalArgumentException {
    if (DBG) {
      Log.d(TAG, "Message. [ip= " + ip + ", message=" + message + ", srcPort=" + srcPort);
    }

    this.ip = ip;
    this.message = message;
    this.mSrcPort = srcPort;
  }

  public String getMessage() {
    return message;
  }

  public int getSrcPort() {
    return mSrcPort;
  }

  public InetAddress getSrcIp() {
    return ip;
  }

  public String toString() {
    return message + "-" + ip + "-" + mSrcPort; // The Blank is for \0.
  }


  public static final String MSG_SERVER_JOIN_BROADCAST_MSG = "broadcast.server.join";
}
