package com.kaazing.demo.simplechat.nio;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import com.kaazing.demo.simplechat.SimpleChatServer;

public class ServerDataEvent {
    public SimpleChatServer server;
    public SocketChannel socket;
    public byte[] data;

    public ServerDataEvent(SimpleChatServer server, SocketChannel socket, byte[] data) {
        this.server = server;
        this.socket = socket;
        this.data = data;
    }

    public String getRemoteAddressStr() {
        try {
            return socket.getRemoteAddress().toString();
        }
        catch (IOException e) {
            return null;
        }
    }

    public String getDataAsString() {
        return new String(data, StandardCharsets.UTF_8);
    }
}