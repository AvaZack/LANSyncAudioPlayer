package com.ava.zack.lansyncaudioplayer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

class AudioDispatcher {
  private ArrayList<ClientInfo> mClients;

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

  private void addClient(Socket socket, InetAddress inetAddress, int port, int channel) {

  }
}
