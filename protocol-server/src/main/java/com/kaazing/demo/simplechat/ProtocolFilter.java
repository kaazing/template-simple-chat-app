package com.kaazing.demo.simplechat;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kaazing.demo.simplechat.nio.ServerDataEvent;

public class ProtocolFilter implements Runnable {
        
    private static final Logger LOGGER = LogManager.getLogger(ProtocolFilter.class.getName());

    private List<ServerDataEvent> queue = new LinkedList<ServerDataEvent>();

    Map<SocketAddress, User> users;

    public void processData(SimpleChatServer server, SocketChannel socket, byte[] data, int count) {
        users = server.getUsers();
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);
        synchronized (queue) {
            queue.add(new ServerDataEvent(server, socket, dataCopy));
            queue.notify();
        }
    }

    public void run() {
        ServerDataEvent dataEvent;

        while (true) {
            // Wait for data to become available
            synchronized (queue) {
                while (queue.isEmpty()) {
                    try {
                        queue.wait();
                    }
                    catch (InterruptedException e) {
                    }
                }
                dataEvent = (ServerDataEvent) queue.remove(0);

                User user = null;

                try {
                    SocketAddress remoteAddress = dataEvent.socket.getRemoteAddress();
                    user = users.get(remoteAddress);
                    if ( user == null ) {
                        user = new User(dataEvent.server, dataEvent.socket);
//                        logger.info(String.format("%s New user sent bytes", user.getLoggerId()));
                        users.put(remoteAddress, user);
//                        user.sendBlah(); // TODO: Delete
                    }
                    else {
                        LOGGER.trace(String.format("%s Received bytes", user.getLoggerId()));
                    }
                }
                catch (IOException e) {
                    LOGGER.error(e);
                }

                LOGGER.debug(String.format("%s Bytes received: %s", user.getLoggerId(), dataEvent.getDataAsString()));
                user.processNewBytes(dataEvent.data);

            }
/*
            String messageStr = "{\"screenData\":\"abcdefghijklmnopqrstuvwxyz\"}";
            String messageLen = padLeftZeros(String.valueOf(messageStr.length()), 4);
            byte[] message = (messageLen + messageStr).getBytes(StandardCharsets.UTF_8);

            byte[] m1 = Arrays.copyOfRange(message, 0, 25);
            byte[] m2 = Arrays.copyOfRange(message, 25, message.length);

            dataEvent.server.send(dataEvent.socket, m1);
            try {
                // Force data to be sent separately to ensure multiple websocket frames
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            dataEvent.server.send(dataEvent.socket, m2);

            // logger.info(String.format("Sending %s", new String(aaa, StandardCharsets.UTF_8)));
             */
        }
    }

    
}
