package com.kaazing.demo.simplechat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class Message {

    /**
     * The total size of the message as given in the message prefix length.
     */
    private int messageSize;
    
    private ArrayList<byte[]> bytesList;

    public Message(int messageSize, byte[] initialBytes) {
        this.messageSize = messageSize;
        bytesList = new ArrayList<byte[]>();
        bytesList.add(initialBytes);
    }

    public int getMessageSize() {
        return messageSize;
    }

    public void addBytes(byte[] bytes) {
        bytesList.add(bytes);
    }

    /**
     * The current length of the bytes received so far
     */
    public int getLength() {
        int len = 0;
        for (byte[] bytes : bytesList) {
            len += bytes.length;
        }
        return len;
    }

    public byte[] getAllBytes() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] bytes : bytesList) {
            try {
                outputStream.write(bytes);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        return outputStream.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("[%s] (%d bytes)", Util.bytesToString(getAllBytes()), getLength());
    }
    
    // For debugging
    public void print() {
        for (byte[] bytes : bytesList) {
            System.out.println(String.format("  => [%s] %d bytes", Util.bytesToString(bytes), bytes.length));
        }
    }

}
