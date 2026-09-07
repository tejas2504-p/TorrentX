package com.torrentx.peer;

import com.torrentx.tracker.PeerInfo;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PeerConnection implements AutoCloseable {
    private final PeerInfo peerInfo;
    private final SocketChannel channel;
    private SelectionKey selectionKey;

    private final ByteBuffer readBuffer;
    private final Queue<ByteBuffer> writeQueue;

    private final PeerStateManager stateManager;
    private long lastActivityTime;
    
    // Remote peer properties
    private byte[] remotePeerId;

    public PeerConnection(PeerInfo peerInfo, SocketChannel channel) {
        this.peerInfo = peerInfo;
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocateDirect(32 * 1024); // 32KB
        this.writeQueue = new ConcurrentLinkedQueue<>();
        this.stateManager = new PeerStateManager();
        this.lastActivityTime = System.currentTimeMillis();
    }

    public PeerInfo getPeerInfo() {
        return peerInfo;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    public Queue<ByteBuffer> getWriteQueue() {
        return writeQueue;
    }

    public PeerConnectionState getState() {
        return stateManager.getState();
    }

    public void transitionState(PeerConnectionState targetState) {
        stateManager.transition(targetState);
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public void updateActivityTime() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public byte[] getRemotePeerId() {
        return remotePeerId;
    }

    public void setRemotePeerId(byte[] remotePeerId) {
        this.remotePeerId = remotePeerId;
    }

    /**
     * Queues data to be written to the peer.
     */
    public void writeData(ByteBuffer data) {
        if (getState() == PeerConnectionState.DISCONNECTED || getState() == PeerConnectionState.CLOSING) {
            return;
        }
        
        // Ensure data is ready to be read from the buffer if it was just constructed
        writeQueue.offer(data);
        
        // Register for OP_WRITE if not already
        if (selectionKey != null && selectionKey.isValid()) {
            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    @Override
    public void close() {
        try {
            transitionState(PeerConnectionState.CLOSING);
            transitionState(PeerConnectionState.DISCONNECTED);
        } catch (IllegalStateException e) {
            // If already FAILED or DISCONNECTED, it's fine
            try {
                if (getState() != PeerConnectionState.DISCONNECTED) {
                    transitionState(PeerConnectionState.DISCONNECTED);
                }
            } catch (Exception ex) {}
        }
        
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            // Ignore on close
        }
    }
}
