package com.kaazing.demo.simplechat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kaazing.demo.simplechat.nio.ChangeRequest;

public class SimpleChatServer implements Runnable {

    // NIO code thanks to http://rox-xmlrpc.sourceforge.net/niotut/

    private static final Logger logger = LogManager.getLogger(SimpleChatServer.class.getName());

    // The host:port combination to listen on
    private InetAddress hostAddress;
    private int port;

    // The channel on which we'll accept connections
    private ServerSocketChannel serverChannel;

    // The selector we'll be monitoring
    private Selector selector;

    // The buffer into which we'll read data when it's available
    private ByteBuffer readBuffer = ByteBuffer.allocate(8192);

    private ProtocolFilter worker;

    // A list of PendingChange instances
    private List<ChangeRequest> pendingChanges = new LinkedList<ChangeRequest>();

    // Maps a SocketChannel to a list of ByteBuffer instances
    private Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<SocketChannel, List<ByteBuffer>>();

    private Map<SocketAddress, User> users;

    private Set<User> agents;

    private Set<User> customers;

    public SimpleChatServer(InetAddress hostAddress, int port, ProtocolFilter worker) throws IOException {
        this.hostAddress = hostAddress;
        this.port = port;
        this.selector = this.initSelector();
        this.worker = worker;
        users = new HashMap<>();
        agents = new HashSet<User>();
        customers = new HashSet<User>();
    }

    public User getUser(String username) {
        //@formatter:off
        Optional<User> user = users.entrySet().stream()
    			.filter(map -> username.equals(map.getValue().getUsername()))
    			.map(Map.Entry::getValue)
                .findFirst();
        //@formatter:on

        if ( user.isPresent() ) {
            return user.get();
        }
        else {
            return null;
        }
    }

    public Map<SocketAddress, User> getUsers() {
        return users;
    }

    public void addAgent(User agent) {
        agents.add(agent);
    }

    public Set<User> getAgents() {
        return agents;
    }
    
    public void addCustomer(User customer) {
        customers.add(customer);
    }

    public Set<User> getCustomers() {
        return customers;
    }
    
    public void send(SocketChannel socket, byte[] data) {
        synchronized (this.pendingChanges) {
            // Indicate we want the interest ops set changed
            this.pendingChanges.add(new ChangeRequest(socket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            // And queue the data we want written
            synchronized (this.pendingData) {
                List<ByteBuffer> queue = (List<ByteBuffer>) this.pendingData.get(socket);
                if ( queue == null ) {
                    queue = new ArrayList<ByteBuffer>();
                    this.pendingData.put(socket, queue);
                }
                queue.add(ByteBuffer.wrap(data));
            }
        }

        // Finally, wake up our selecting thread so it can make the required changes
        this.selector.wakeup();
    }

    public void run() {
        while (true) {
            try {
                // Process any pending changes
                synchronized (this.pendingChanges) {
                    Iterator<ChangeRequest> changes = this.pendingChanges.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type) {
                        case ChangeRequest.CHANGEOPS:
                            SelectionKey key = change.socket.keyFor(this.selector);
                            key.interestOps(change.ops);
                        }
                    }
                    this.pendingChanges.clear();
                }

                // Wait for an event one of the registered channels
                this.selector.select();

                // Iterate over the set of keys for which events are available
                Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if ( !key.isValid() ) {
                        continue;
                    }

                    // Check what event is available and deal with it
                    if ( key.isAcceptable() ) {
                        this.accept(key);
                    }
                    else if ( key.isReadable() ) {
                        this.read(key);
                    }
                    else if ( key.isWritable() ) {
                        this.write(key);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        // For an accept to be pending the channel must be a server socket channel.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        // Register the new SocketChannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        socketChannel.register(this.selector, SelectionKey.OP_READ);
        logger.info(String.format("[%s] New connection", socketChannel.getRemoteAddress()));
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Clear out our read buffer so it's ready for new data
        this.readBuffer.clear();

        // Attempt to read off the channel
        int numRead;
        try {
            numRead = socketChannel.read(this.readBuffer);
        }
        catch (IOException e) {
            // The remote forcibly closed the connection, cancel
            // the selection key and close the channel.
            User user = users.get(socketChannel.getRemoteAddress());
            String loggerId;
            if ( user != null ) {
                loggerId = user.getLoggerId();
            }
            else {
                loggerId = String.format("[%s]", socketChannel.getRemoteAddress().toString());
            }
            logger.info(String.format("%s Remote end closed the connection", loggerId));
            if ( user != null ) {
                user.handleConnectionClose();
            }
            key.cancel();
            socketChannel.close();
            return;
        }

        if ( numRead == -1 ) {
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            User user = users.get(socketChannel.getRemoteAddress());
            String loggerId;
            if ( user != null ) {
                loggerId = user.getLoggerId();
            }
            else {
                loggerId = String.format("[%s]", socketChannel.getRemoteAddress().toString());
            }
            logger.info(String.format("%s Remote end closed the connection", loggerId));
            if ( user != null ) {
                user.handleConnectionClose();
            }
            key.channel().close();
            key.cancel();
            return;
        }

        // Hand the data off to our worker thread
        this.worker.processData(this, socketChannel, this.readBuffer.array(), numRead);
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (this.pendingData) {
            List<?> queue = (List<?>) this.pendingData.get(socketChannel);

            // Write until there's not more data ...
            while (!queue.isEmpty()) {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                socketChannel.write(buf);
                if ( buf.remaining() > 0 ) {
                    // ... or the socket's buffer fills up
                    break;
                }
                queue.remove(0);
            }

            if ( queue.isEmpty() ) {
                // We wrote away all data, so we're no longer interested
                // in writing on this socket. Switch back to waiting for
                // data.
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private Selector initSelector() throws IOException {
        // Create a new selector
        Selector socketSelector = SelectorProvider.provider().openSelector();

        // Create a new non-blocking server socket channel
        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        logger.info(String.format("Server started on %s:%d", this.hostAddress, this.port));
        // Bind the server socket to the specified address and port
        InetSocketAddress isa = new InetSocketAddress(this.hostAddress, this.port);
        serverChannel.socket().bind(isa);

        // Register the server socket channel, indicating an interest in
        // accepting new connections
        serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

        return socketSelector;
    }

    public static void main(String[] args) {
        try {
            ProtocolFilter worker = new ProtocolFilter();
            InetAddress host;
            int port = 4445;
            new Thread(worker).start();
            if (args.length == 2) {
                host = InetAddress.getByName(args[0]);
                port = Integer.parseInt(args[1]);
            } else if (args.length == 1) {
                host = InetAddress.getByName(args[0]);
            } else {
                host = InetAddress.getByName("localhost");
            }
            // TODO: Default host and port, but output that it happened and allow it to be
            // overridden with parameters.
            // if (args.length < 2) {
            // logger.info("You didn't specify a host and port");
            // logger.info("Usage: java -jar uber-protocol-server-VERSION.jar 127.0.0.1 4445");
            // System.exit(0);
            // }
            new Thread(new SimpleChatServer(host, port, worker)).start();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}