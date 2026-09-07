package com.torrentx.peer;

import com.torrentx.network.BitTorrentProtocolHandler;
import com.torrentx.network.Handshake;
import com.torrentx.network.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PeerConnection.class);
    private static final int SOCKET_TIMEOUT_MS = 120_000;

    public enum State {
        CONNECTING, HANDSHAKE, CONNECTED, DISCONNECTING, DISCONNECTED
    }

    private final Peer peer;
    private final Socket socket;
    private final BitTorrentProtocolHandler protocolHandler;
    private final BlockingQueue<Message> outboundQueue = new LinkedBlockingQueue<>();
    private final MessageListener messageListener;
    private volatile State state = State.CONNECTING;
    
    private Thread readThread;
    private Thread writeThread;

    public interface MessageListener {
        void onHandshakeReceived(PeerConnection connection, Handshake handshake);
        void onMessageReceived(PeerConnection connection, Message message);
        void onDisconnected(PeerConnection connection);
    }

    public PeerConnection(Peer peer, Socket socket, BitTorrentProtocolHandler protocolHandler, MessageListener messageListener) {
        this.peer = peer;
        this.socket = socket;
        this.protocolHandler = protocolHandler;
        this.messageListener = messageListener;
    }

    public void start(Handshake localHandshake) throws IOException {
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        this.state = State.HANDSHAKE;

        this.writeThread = Thread.ofVirtual().name("PeerWrite-" + socket.getRemoteSocketAddress()).start(() -> writeLoop(localHandshake));
        this.readThread = Thread.ofVirtual().name("PeerRead-" + socket.getRemoteSocketAddress()).start(this::readLoop);
    }

    private void writeLoop(Handshake localHandshake) {
        try (OutputStream out = socket.getOutputStream()) {
            protocolHandler.writeHandshake(localHandshake, out);
            
            while (state != State.DISCONNECTING && state != State.DISCONNECTED) {
                Message msg = outboundQueue.take();
                protocolHandler.writeMessage(msg, out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error in write loop for {}: {}", peer.getInfo().getIp(), e.getMessage());
            disconnect();
        }
    }

    private void readLoop() {
        try (InputStream in = socket.getInputStream()) {
            Handshake remoteHandshake = protocolHandler.readHandshake(in);
            messageListener.onHandshakeReceived(this, remoteHandshake);
            
            state = State.CONNECTED;
            
            while (state != State.DISCONNECTING && state != State.DISCONNECTED) {
                Message msg = protocolHandler.readMessage(in);
                messageListener.onMessageReceived(this, msg);
            }
        } catch (Exception e) {
            log.error("Error in read loop for {}: {}", peer.getInfo().getIp(), e.getMessage());
            disconnect();
        }
    }

    public void sendMessage(Message message) {
        if (state == State.CONNECTED) {
            outboundQueue.offer(message);
        }
    }

    public void disconnect() {
        if (state == State.DISCONNECTING || state == State.DISCONNECTED) {
            return;
        }
        state = State.DISCONNECTING;
        try {
            socket.close();
        } catch (IOException e) {
            log.warn("Error closing socket", e);
        }
        
        if (readThread != null) readThread.interrupt();
        if (writeThread != null) writeThread.interrupt();
        
        state = State.DISCONNECTED;
        if (messageListener != null) {
            messageListener.onDisconnected(this);
        }
    }

    @Override
    public void close() {
        disconnect();
    }
    
    public State getState() {
        return state;
    }

    public Peer getPeer() {
        return peer;
    }
}
