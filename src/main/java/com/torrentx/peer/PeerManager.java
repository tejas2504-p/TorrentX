package com.torrentx.peer;

import com.torrentx.bencode.Metainfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeerManager implements PeerService {
    private static final Logger logger = LoggerFactory.getLogger(PeerManager.class);

    private final Metainfo metainfo;
    private final byte[] localPeerId;
    private final int port;
    private PeerConnection.PeerConnectionListener peerListener;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private final Map<SocketChannel, PeerConnection> activeConnections = new ConcurrentHashMap<>();
    
    private volatile boolean running = false;
    private Thread selectorThread;

    public PeerManager(Metainfo metainfo, byte[] localPeerId, int port) {
        this.metainfo = metainfo;
        this.localPeerId = localPeerId;
        this.port = port;
    }

    public PeerManager(Metainfo metainfo, byte[] localPeerId, int port, PeerConnection.PeerConnectionListener peerListener) {
        this.metainfo = metainfo;
        this.localPeerId = localPeerId;
        this.port = port;
        this.peerListener = peerListener;
    }

    @Override
    public void setListener(PeerConnection.PeerConnectionListener listener) {
        this.peerListener = listener;
    }

    public synchronized void start() throws IOException {
        if (running) return;

        this.selector = Selector.open();
        
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(new InetSocketAddress(port));
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.running = true;
        this.selectorThread = new Thread(this::runSelectorLoop, "torrentx-selector-thread");
        this.selectorThread.start();
        logger.info("PeerManager listening on port {}", port);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (selector != null) {
            selector.wakeup();
        }
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (selector != null) {
                selector.close();
            }
        } catch (IOException e) {
            logger.error("Error shutting down channels", e);
        }

        for (PeerConnection conn : activeConnections.values()) {
            closeConnection(conn);
        }
        activeConnections.clear();
        logger.info("PeerManager stopped");
    }

    public void connectToPeers(List<Peer> peers) {
        if (!running) return;

        for (Peer peer : peers) {
            if (activeConnections.values().stream().anyMatch(c -> c.getPeer().equals(peer))) {
                continue;
            }

            try {
                logger.info("Initiating outbound connection to peer {}", peer);
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                socketChannel.connect(peer.getAddress());

                PeerConnection conn = new PeerConnection(peer, socketChannel, metainfo);
                activeConnections.put(socketChannel, conn);

                selector.wakeup();
                socketChannel.register(selector, SelectionKey.OP_CONNECT);
            } catch (IOException e) {
                logger.error("Failed to initiate connection to peer {}", peer, e);
            }
        }
    }

    public Collection<PeerConnection> getActiveConnections() {
        return activeConnections.values();
    }

    private void runSelectorLoop() {
        while (running) {
            try {
                int selectCount = selector.select();
                if (selectCount == 0) {
                    continue;
                }

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    try {
                        if (key.isAcceptable()) {
                            handleAccept();
                        }
                        if (key.isConnectable()) {
                            handleConnect(key);
                        }
                        if (key.isReadable()) {
                            handleRead(key);
                        }
                        if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException e) {
                        logger.error("Error processing key for channel", e);
                        handleClose(key);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    logger.error("Error in selector loop", e);
                }
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        
        Peer peer = new Peer((InetSocketAddress) clientChannel.getRemoteAddress());
        PeerConnection conn = new PeerConnection(peer, clientChannel, metainfo);
        
        activeConnections.put(clientChannel, conn);
        clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        logger.info("Accepted incoming peer connection from {}", peer);
        
        conn.sendHandshake(localPeerId);
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        PeerConnection conn = activeConnections.get(socketChannel);
        
        if (socketChannel.finishConnect()) {
            logger.info("Outbound connection established with {}", conn.getPeer());
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            conn.sendHandshake(localPeerId);
        } else {
            throw new IOException("Failed to finish connection to " + conn.getPeer());
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        PeerConnection conn = activeConnections.get(socketChannel);
        
        if (conn == null) {
            key.cancel();
            socketChannel.close();
            return;
        }

        boolean EOF = conn.readAndProcess(peerListener);
        if (EOF) {
            logger.info("Peer {} disconnected (EOF)", conn.getPeer());
            handleClose(key);
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        PeerConnection conn = activeConnections.get(socketChannel);
        
        if (conn != null) {
            conn.write();
        }
    }

    private void handleClose(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        PeerConnection conn = activeConnections.remove(channel);
        if (conn != null) {
            closeConnection(conn);
        }
        key.cancel();
    }

    private void closeConnection(PeerConnection conn) {
        try {
            conn.getSocketChannel().close();
        } catch (IOException e) {
            logger.error("Failed to close socket for {}", conn.getPeer(), e);
        }
    }
}
