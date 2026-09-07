package com.torrentx.peer;

import com.torrentx.tracker.PeerInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PeerManager implements AutoCloseable {
    private static final int MAX_CONNECTIONS = 50;
    private static final long HANDSHAKE_TIMEOUT_MS = 15000;
    private static final long KEEPALIVE_TIMEOUT_MS = 120000;

    private final byte[] localInfoHash;
    private final byte[] localPeerId;
    
    private final Selector selector;
    private final ProtocolHandler protocolHandler;
    
    private final Map<SocketChannel, PeerConnection> activeConnections = new ConcurrentHashMap<>();
    private final Queue<PeerInfo> pendingPeers = new ConcurrentLinkedQueue<>();
    
    private final ScheduledExecutorService timeoutExecutor;
    private volatile boolean running = false;
    private Thread reactorThread;

    public PeerManager(byte[] localInfoHash, byte[] localPeerId) throws IOException {
        this.localInfoHash = localInfoHash;
        this.localPeerId = localPeerId;
        this.selector = Selector.open();
        this.protocolHandler = new ProtocolHandler(localInfoHash, localPeerId);
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public void addPeers(Iterable<PeerInfo> peers) {
        for (PeerInfo peer : peers) {
            pendingPeers.offer(peer);
        }
        selector.wakeup();
    }

    public void start() {
        if (running) return;
        running = true;
        
        reactorThread = new Thread(this::runReactorLoop, "PeerManager-Reactor");
        reactorThread.start();
        
        timeoutExecutor.scheduleAtFixedRate(this::checkTimeouts, 5, 5, TimeUnit.SECONDS);
    }

    private void runReactorLoop() {
        while (running) {
            try {
                // Try to establish new connections if we are below the limit
                while (activeConnections.size() < MAX_CONNECTIONS) {
                    PeerInfo peer = pendingPeers.poll();
                    if (peer == null) break;
                    initiateConnection(peer);
                }

                int readyChannels = selector.select(1000); // 1 second timeout
                if (readyChannels == 0) continue;

                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (!key.isValid()) continue;

                    PeerConnection connection = (PeerConnection) key.attachment();
                    
                    try {
                        if (key.isConnectable()) {
                            handleConnect(key, connection);
                        }
                        if (key.isReadable()) {
                            handleRead(key, connection);
                        }
                        if (key.isWritable()) {
                            handleWrite(key, connection);
                        }
                    } catch (Exception e) {
                        System.err.println("Connection error for " + connection.getPeerInfo() + ": " + e.getMessage());
                        disconnect(connection);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Selector error: " + e.getMessage());
                }
            }
        }
    }

    private void initiateConnection(PeerInfo peer) {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            
            PeerConnection connection = new PeerConnection(peer, channel);
            SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT, connection);
            connection.setSelectionKey(key);
            
            channel.connect(new InetSocketAddress(peer.getIp(), peer.getPort()));
            activeConnections.put(channel, connection);
        } catch (IOException e) {
            System.err.println("Failed to initiate connection to " + peer + ": " + e.getMessage());
        }
    }

    private void handleConnect(SelectionKey key, PeerConnection connection) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        if (channel.finishConnect()) {
            key.interestOps(SelectionKey.OP_READ);
            connection.updateActivityTime();
            protocolHandler.handleConnect(connection);
        } else {
            disconnect(connection);
        }
    }

    private void handleRead(SelectionKey key, PeerConnection connection) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer readBuffer = connection.getReadBuffer();
        
        int bytesRead = channel.read(readBuffer);
        if (bytesRead == -1) {
            // EOF
            disconnect(connection);
            return;
        }
        
        if (bytesRead > 0) {
            connection.updateActivityTime();
            try {
                protocolHandler.handleRead(connection);
            } catch (Exception e) {
                System.err.println("Protocol error from " + connection.getPeerInfo() + ": " + e.getMessage());
                disconnect(connection);
            }
        }
    }

    private void handleWrite(SelectionKey key, PeerConnection connection) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        Queue<ByteBuffer> writeQueue = connection.getWriteQueue();
        
        while (!writeQueue.isEmpty()) {
            ByteBuffer buffer = writeQueue.peek();
            channel.write(buffer);
            connection.updateActivityTime();
            
            if (buffer.hasRemaining()) {
                // Partial write, channel is full. Stop writing and wait for next OP_WRITE
                return;
            } else {
                // Fully written, remove from queue
                writeQueue.poll();
            }
        }
        
        if (writeQueue.isEmpty()) {
            // Nothing more to write, remove OP_WRITE
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (PeerConnection connection : activeConnections.values()) {
            long idleTime = now - connection.getLastActivityTime();
            
            if (connection.getState() == PeerConnection.ConnectionState.HANDSHAKING && idleTime > HANDSHAKE_TIMEOUT_MS) {
                System.out.println("Handshake timeout for " + connection.getPeerInfo());
                disconnect(connection);
            } else if (idleTime > KEEPALIVE_TIMEOUT_MS) {
                System.out.println("Keep-alive timeout for " + connection.getPeerInfo());
                disconnect(connection);
            }
        }
    }

    public void disconnect(PeerConnection connection) {
        activeConnections.remove(connection.getChannel());
        connection.close();
    }

    @Override
    public void close() {
        running = false;
        selector.wakeup();
        if (reactorThread != null) {
            try {
                reactorThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        timeoutExecutor.shutdownNow();
        
        for (PeerConnection connection : activeConnections.values()) {
            disconnect(connection);
        }
        
        try {
            selector.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
