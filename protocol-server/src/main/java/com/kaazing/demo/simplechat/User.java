package com.kaazing.demo.simplechat;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class User {

    private static final Logger LOGGER = LogManager.getLogger(User.class.getName());

    /**
     * The length of the length prefix, indicating how long the message is.
     */
    public static final int LENGTH_PREFIX_LENGTH = 4;

    public enum Role {
        customer, agent
    }

    private SimpleChatServer server;

    private SocketChannel socket;
    private SocketAddress remoteAddress;

    /**
     * Store the bytes from the length prefix until we've received them all.
     */
    private StringBuffer currentLenthPrefix = new StringBuffer(LENGTH_PREFIX_LENGTH);

    private Message currentMessage;

    private String username;
    private Role role;

    /**
     * A convenience for logging to uniquely identify a connection. Show the
     * remote address and username. Use only the remote address until the
     * username is known.
     */
    private String loggerId;

    public User(SimpleChatServer server, SocketChannel socket) {
        this.server = server;
        this.socket = socket;
        try {
            this.remoteAddress = socket.getRemoteAddress();
        }
        catch (IOException e) {
            LOGGER.error(e);
        }
        loggerId = String.format("[%s]", remoteAddress);
    }

    public void setUsername(String username) {
        this.username = username;
        loggerId = String.format("[%s-%s]", remoteAddress, username);
    }

    public String getLoggerId() {
        return loggerId;
    }

    public String toString() {
        return String.format("%s[address=%s,username=%s,role=%s]", this.getClass().getSimpleName(), remoteAddress, username, role);
    }

    /*
     * public static void main(String[] args) { User u = new User(null);
     * 
     * u.test();
     * 
     * if (1==1) return; String s;
     * 
     * s = "0"; u.processNewBytes(Util.stringToBytes(s));
     * 
     * s = "04"; u.processNewBytes(Util.stringToBytes(s));
     * 
     * s = "3"; u.processNewBytes(Util.stringToBytes(s));
     * 
     * s = "{\"screenData\":\"abcdefghijklmn";
     * u.processNewBytes(Util.stringToBytes(s));
     * 
     * s = "opqrstuvwxyz\"}00"; u.processNewBytes(Util.stringToBytes(s));
     * 
     * s = "2"; u.processNewBytes(Util.stringToBytes(s));
     * 
     * s = "2{\"screenData\":\"ABCDE\"}0026{\"screenData\":\"FGHIJKLMN\"}";
     * u.processNewBytes(Util.stringToBytes(s));
     * 
     * }
     * 
     * public void test() { JSONObject j = new JSONObject(
     * "{\"firstName\": \"John\", \"lastName\": \"Smith\", \"age\": 25,\"address\" : {\"streetAddress\": \"21 2nd Street\",\"city\": \"New York\",\"state\": \"NY\",\"postalCode\": \"10021\"},\"phoneNumber\": [{ \"type\": \"home\", \"number\": \"212 555-1234\" },{ \"type\": \"fax\", \"number\": \"646 555-4567\" }]}"
     * ); System.out.println(j.toString()); for (String k :
     * JSONObject.getNames(j)) { System.out.println(k); }
     * System.out.println("----------------");
     * System.out.println(j.getString("firstNames")); }
     */

    /**
     * Process new bytes that just arrived for this user
     * 
     * @param bytes
     */
    public void processNewBytes(byte[] bytes) {
        // If currentMessage is null, then we are starting a new message.
        // Otherwise we are continuing an existing
        // message.
        if ( currentMessage == null ) {
            // This is a new message
            LOGGER.trace(String.format("%s Begin processing new message.", loggerId));
            // Determine the length. But make sure we've received all of the
            // length prefix, though.
            int remainingLengthPrefixNeeded = LENGTH_PREFIX_LENGTH - currentLenthPrefix.length();
            int lengthPrefixToGet = Math.min(remainingLengthPrefixNeeded, bytes.length);
            byte[] lenBytes = Arrays.copyOfRange(bytes, 0, lengthPrefixToGet);
            currentLenthPrefix.append(new String(lenBytes, StandardCharsets.UTF_8));

            LOGGER.trace(String.format("%s Length string: %s", loggerId, currentLenthPrefix));
            if ( currentLenthPrefix.length() < LENGTH_PREFIX_LENGTH ) {
                // Not enough of the prefix length yet. Wait for more bytes.
                return;
            }

            byte[] remainingBytes = Arrays.copyOfRange(bytes, lengthPrefixToGet, bytes.length);
            if ( remainingBytes.length == 0 ) {
                return;
            }

            int len = Integer.valueOf(currentLenthPrefix.toString());
            currentLenthPrefix = new StringBuffer(LENGTH_PREFIX_LENGTH);
            LOGGER.trace(String.format("%s Message size: %d bytes", loggerId, len));

            // See if we've received all the bytes, otherwise store them until
            // more bytes arrive.
            if ( remainingBytes.length >= len ) {
                byte[] messageBytes = Arrays.copyOfRange(remainingBytes, 0, len);
                LOGGER.debug(String.format("%s Got all the bytes: [%s]", loggerId, Util.bytesToString(messageBytes)));
                processMessage(new Message(len, messageBytes));

                // Check if the next message is in this set of bytes.
                if ( remainingBytes.length > len ) {
                    byte[] nextMessageBytes = Arrays.copyOfRange(remainingBytes, len, remainingBytes.length);
                    processNewBytes(nextMessageBytes);
                }
            }
            else {
                byte[] messageBytes = Arrays.copyOfRange(remainingBytes, 0, remainingBytes.length);
                LOGGER.debug(String.format("%s Got some of the bytes: [%s]", loggerId, Util.bytesToString(messageBytes)));
                currentMessage = new Message(len, messageBytes);
            }
        }
        else {
            // This is the continuation of an existing message
            int remainingLen = currentMessage.getMessageSize() - currentMessage.getLength();
            LOGGER.info(
                    String.format("%s Continuing an existing message. Number of bytes still needed: %d", loggerId, remainingLen));

            // See if we've received all the bytes, otherwise store them until
            // more bytes arrive.
            if ( bytes.length >= remainingLen ) {
                byte[] messageBytes = Arrays.copyOfRange(bytes, 0, remainingLen);
                LOGGER.debug(String.format("%s Got all the bytes: [%s]", loggerId, Util.bytesToString(messageBytes)));
                currentMessage.addBytes(messageBytes);
                processMessage(currentMessage);
                currentMessage = null;

                // Check if the next message is in this set of bytes.
                if ( bytes.length > remainingLen ) {
                    byte[] nextMessageBytes = Arrays.copyOfRange(bytes, remainingLen, bytes.length);
                    processNewBytes(nextMessageBytes);
                }
            }
            else {
                byte[] messageBytes = Arrays.copyOfRange(bytes, 0, bytes.length);
                LOGGER.debug(String.format("%s Got some of the bytes: [%s]", loggerId, Util.bytesToString(messageBytes)));
                currentMessage.addBytes(messageBytes);
            }
        }
    }

    /**
     * Process a fully assembled message.
     */
    private void processMessage(Message message) {
        LOGGER.info(String.format("%s Processing full message: %s", loggerId, message.toString()));

        JSONObject json = new JSONObject(Util.bytesToString(message.getAllBytes()));

        String messageType = null;
        try {
            messageType = json.getString("type");
        }
        catch (JSONException e) {
            LOGGER.error("No messageType", e);
            return;
        }

        LOGGER.info(String.format("%s Message type: %s", loggerId, messageType));

        switch (messageType) {
        case "credentials":
            processCredentialsMessage(json);
            break;
        case "message":
            processChatMessage(json);
            break;
        default:
            LOGGER.error(String.format("Unknown messageType: [%s]", messageType));
        }

    }

    private void processCredentialsMessage(JSONObject message) {
        try {
            setUsername(message.getString("username"));
        }
        catch (JSONException e) {
            LOGGER.error(String.format("%s No username", loggerId), e);
        }

        try {
            String roleStr = message.getString("role");
            role = Role.valueOf(roleStr.toLowerCase());
        }
        catch (JSONException e) {
            LOGGER.error(String.format("%s No role", loggerId), e);
        }

        LOGGER.info(String.format("%s %s", loggerId, this));

        // TODO: Reject user if the username already exists

        switch (role) {
        case customer:
            LOGGER.debug(String.format("%s Adding user to customer list", loggerId));
            server.addCustomer(this);

            // If tell the customer that there are no agents.
            // otherwise there are agents, tell them that a new customer has
            // arrived.
            if ( server.getAgents().size() > 0 ) {
                sendAgentsPresentMessage();
                for (User agent : server.getAgents()) {
                    agent.sendUserConnectedMessage(this);
                }
            }
            break;
        case agent:
            LOGGER.debug(String.format("%s Adding user to agent list", loggerId));
            server.addAgent(this);
            // Tell the new agent about all of the customers known so far.
            boolean firstAgent = (server.getAgents().size() == 1);
            for (User customer : server.getCustomers()) {
                sendUserConnectedMessage(customer);
                if ( firstAgent ) {
                    customer.sendAgentsPresentMessage();
                }
            }

        }

    }

    public String getUsername() {
        return username;
    }

    public void sendUserConnectedMessage(User newUser) {
        LOGGER.info(String.format("%s Notifying %s (%s) that %" + "s (%s) is present", loggerId, username, role, newUser.username,
                newUser.role));
        String str = String.format("{\"type\":\"connected\",\"username\":\"%s\",\"role\":\"%s\"}", newUser.getUsername(),
                newUser.role);
        String strLen = Util.padLeftZeros(String.valueOf(str.length()), LENGTH_PREFIX_LENGTH);
        LOGGER.debug(String.format("%s str=%s len=%s", loggerId, str, strLen));

        byte[] bytes = (strLen + str).getBytes(StandardCharsets.UTF_8);
        server.send(socket, bytes);
    }

    public void sendUserDisconnectedMessage(User user) {
        LOGGER.info(String.format("%s Notifying %s (%s) that %" + "s (%s) disconnected", loggerId, username, role, user.username,
                user.role));
        String str = String.format("{\"type\":\"disconnected\",\"username\":\"%s\"}", user.getUsername());
        String strLen = Util.padLeftZeros(String.valueOf(str.length()), LENGTH_PREFIX_LENGTH);
        LOGGER.debug(String.format("%s str=%s len=%s", loggerId, str, strLen));

        byte[] bytes = (strLen + str).getBytes(StandardCharsets.UTF_8);
        server.send(socket, bytes);
    }

    public void sendNoAgentsPresentMessage() {
        LOGGER.info(String.format("%s Notifying %s that there are no agents present", loggerId, username));
        String str = "{\"type\":\"NoAgentsPresent\"}";
        String strLen = Util.padLeftZeros(String.valueOf(str.length()), LENGTH_PREFIX_LENGTH);
        LOGGER.debug(String.format("%s str=%s len=%s", loggerId, str, strLen));

        byte[] bytes = (strLen + str).getBytes(StandardCharsets.UTF_8);
        server.send(socket, bytes);
    }

    public void sendAgentsPresentMessage() {
        LOGGER.info(String.format("%s Notifying %s that there are agents are present", loggerId, username));
        String str = "{\"type\":\"AgentsPresent\"}";
        String strLen = Util.padLeftZeros(String.valueOf(str.length()), LENGTH_PREFIX_LENGTH);
        LOGGER.debug(String.format("%s str=%s len=%s", loggerId, str, strLen));

        byte[] bytes = (strLen + str).getBytes(StandardCharsets.UTF_8);
        server.send(socket, bytes);
    }

    public void sendCustomerChatMessage(User sender, String messageText) {
        String str = String.format("{\"type\":\"customerMessage\",\"sender\":\"%s\",\"messageText\":\"%s\"}", sender.getUsername(),
                messageText);
        String strLen = Util.padLeftZeros(String.valueOf(str.length()), LENGTH_PREFIX_LENGTH);
        LOGGER.info(String.format("%s Sending message to %s", loggerId, username));
        LOGGER.info(String.format("%s message=%s len=%s", loggerId, str, strLen));

        byte[] bytes = (strLen + str).getBytes(StandardCharsets.UTF_8);
        server.send(socket, bytes);
    }

    public void sendAgentChatMessage(User sender, User recipient, String messageText) {
        String str = String.format("{\"type\":\"agentMessage\",\"sender\":\"%s\",\"to\":\"%s\",\"messageText\":\"%s\"}",
                sender.getUsername(), recipient.username, messageText);
        String strLen = Util.padLeftZeros(String.valueOf(str.length()), LENGTH_PREFIX_LENGTH);
        LOGGER.info(String.format("%s Sending message to %s", loggerId, username));
        LOGGER.info(String.format("%s message=%s len=%s", loggerId, str, strLen));

        byte[] bytes = (strLen + str).getBytes(StandardCharsets.UTF_8);
        server.send(socket, bytes);
    }

    private void processChatMessage(JSONObject message) {
        String messageText = null;
        try {
            messageText = message.getString("messageText");
            // JSON encode the string in case the message contained characters
            // that need escaping, like double quotes or backslashes.
            messageText = JSONObject.quote(messageText);
            // The encoding adds surrounding quotes which we don't need. So remove them.
            messageText = messageText.substring(1, messageText.length()-1);
            LOGGER.info(String.format("%s messageText=%s", loggerId, messageText));
        }
        catch (JSONException e) {
            LOGGER.error(String.format("%s No messageText", loggerId), e);
        }

        switch (role) {
        case customer:
            // Send the message to agents
            for (User agent : server.getAgents()) {
                LOGGER.debug(String.format("%s Sending message to %s", loggerId, agent));
                agent.sendCustomerChatMessage(this, messageText);
            }
            break;
        case agent:
            // Send the message to the customer
            String to = null;
            try {
                to = message.getString("to");
                LOGGER.info(String.format("%s to=%s", loggerId, to));
            }
            catch (JSONException e) {
                LOGGER.error(String.format("%s No to", loggerId), e);
            }

            User toUser = server.getUser(to);
            if ( toUser == null ) {
                LOGGER.error(String.format("Could not find user %s", loggerId, to));
                return;
            }
            toUser.sendAgentChatMessage(this, toUser, messageText);
            // Also let other agents see the conversation.
            for (User agent : server.getAgents()) {
                if ( this == agent ) {
                    // Don't send to ourselves.
                    continue;
                }
                agent.sendAgentChatMessage(this, toUser, messageText);
            }
            break;
        }

        // For testing
        if ( messageText.compareToIgnoreCase("hitme") == 0 ) {
            sendTestMessages();
        }
    }

    private void sendTestMessages() {

        // Send a message that will span multiple WebSocket frames. This
        // includes the length prefix spanning
        // multiple frames.
        String testMessage1Str = "{\"type\":\"agentMessage\", \"message\":\"0045abcdefghijklmnopqrstuvwxyz\"}";
        String testMessage1Len = Util.padLeftZeros(String.valueOf(testMessage1Str.length()), LENGTH_PREFIX_LENGTH);
        byte[] testMessage1 = (testMessage1Len + testMessage1Str).getBytes(StandardCharsets.UTF_8);

        byte[] m1 = Arrays.copyOfRange(testMessage1, 0, 2);
        byte[] m2 = Arrays.copyOfRange(testMessage1, 2, 25);
        byte[] m3 = Arrays.copyOfRange(testMessage1, 25, 39);
        byte[] m4 = Arrays.copyOfRange(testMessage1, 39, testMessage1.length);

        LOGGER.info(String.format("%s Sending test message1: %s%s", loggerId, testMessage1Len, testMessage1Str));

        try {
            // Force data to be sent separately to ensure multiple WebSocket
            // frames
            server.send(socket, m1);
            Thread.sleep(10);
            server.send(socket, m2);
            Thread.sleep(10);
            server.send(socket, m3);
            Thread.sleep(10);
            server.send(socket, m4);
            Thread.sleep(10);
        }
        catch (InterruptedException e) {
            LOGGER.error(String.format("%s I can't sleep", loggerId), e);
        }

        // Send two messages that will be in the same WebSocket frame.
        String testMessage3Str = "{\"type\":\"agentMessage\", \"message\":\"Hello, world!\"}";
        String testMessage3Len = Util.padLeftZeros(String.valueOf(testMessage3Str.length()), LENGTH_PREFIX_LENGTH);
        String testMessage4Str = "{\"type\":\"agentMessage\", \"message\":\"woot\"}";
        String testMessage4Len = Util.padLeftZeros(String.valueOf(testMessage4Str.length()), LENGTH_PREFIX_LENGTH);
        testMessage1 = (testMessage3Len + testMessage3Str + testMessage4Len + testMessage4Str).getBytes(StandardCharsets.UTF_8);
        LOGGER.info(String.format("%s Sending test message2: %s%s", loggerId, testMessage3Len, testMessage3Str));
        LOGGER.info(String.format("%s Sending test message3: %s%s", loggerId, testMessage4Len, testMessage4Str));
        server.send(socket, testMessage1);
    }

    public void handleConnectionClose() {
        LOGGER.trace(String.format("%s Connection closed. Cleaning up", loggerId));
        server.getUsers().remove(remoteAddress);
        switch (role) {
        case customer:
            server.getCustomers().remove(this);
            // Tell all agents that customer is gone
            for (User agent : server.getAgents()) {
                agent.sendUserDisconnectedMessage(this);
            }
            break;
        case agent:
            server.getAgents().remove(this);
            // If this was the last agent, then tell customers.
            if ( server.getAgents().size() == 0 ) {
                for (User customer : server.getCustomers()) {
                    customer.sendNoAgentsPresentMessage();
                }
            }
            break;
        }
    }

}
