package com.torrentx.upload;

import com.torrentx.peer.Peer;
import com.torrentx.peer.PeerConnection;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages the upload lifecycle of peers, tracking interested states,
 * choking/unchoking, and restricting simultaneous upload slots.
 */
public class UploadManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(UploadManager.class.getName());

    private final Map<PeerConnection, Boolean> uploadPeers = new ConcurrentHashMap<>();
    private final int maxUploadSlots;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running;

    public UploadManager(int maxUploadSlots) {
        if (maxUploadSlots <= 0) {
            throw new IllegalArgumentException("Max upload slots must be positive");
        }
        this.maxUploadSlots = maxUploadSlots;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        if (running) return;
        running = true;
        // Run slot management every 10 seconds
        scheduler.scheduleAtFixedRate(this::manageUploadSlots, 0, 10, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
        uploadPeers.clear();
    }

    public void registerPeer(PeerConnection connection) {
        if (connection != null) {
            // New peers are choked by default (Peer.choked = true)
            uploadPeers.put(connection, Boolean.TRUE);
        }
    }

    public void unregisterPeer(PeerConnection connection) {
        if (connection != null) {
            uploadPeers.remove(connection);
            recalculateSlots(); // Re-evaluate slots when someone leaves
        }
    }

    public int getRegisteredPeerCount() {
        return uploadPeers.size();
    }

    /**
     * Executes the choking/unchoking strategy safely in a background thread.
     */
    public synchronized void manageUploadSlots() {
        if (!running) return;

        List<PeerConnection> interestedPeers = new ArrayList<>();
        List<PeerConnection> notInterestedPeers = new ArrayList<>();

        // 1. Separate peers into interested and not interested
        for (PeerConnection connection : uploadPeers.keySet()) {
            if (!connection.getChannel().isOpen()) {
                uploadPeers.remove(connection);
                continue;
            }
            if (connection.getPeerState().isInterestedInMe()) {
                interestedPeers.add(connection);
            } else {
                notInterestedPeers.add(connection);
            }
        }

        // 2. Choke all peers that are no longer interested
        for (PeerConnection connection : notInterestedPeers) {
            if (!connection.getPeerState().isChoked()) {
                chokePeer(connection);
            }
        }

        // 3. Select which interested peers to unchoke.
        // For Phase 8, we use a simple shuffle for fairness (preventing starvation).
        // Future optimizations can sort by download rate (Tit-for-tat).
        Collections.shuffle(interestedPeers);

        int unchokedCount = 0;
        for (PeerConnection connection : interestedPeers) {
            if (unchokedCount < maxUploadSlots) {
                if (connection.getPeerState().isChoked()) {
                    unchokePeer(connection);
                }
                unchokedCount++;
            } else {
                if (!connection.getPeerState().isChoked()) {
                    chokePeer(connection);
                }
            }
        }
    }

    /**
     * Triggers an immediate recalculation of upload slots.
     * Useful when a peer suddenly becomes interested or disconnects.
     */
    public void recalculateSlots() {
        if (running) {
            scheduler.execute(this::manageUploadSlots);
        }
    }

    private void chokePeer(PeerConnection connection) {
        Peer peer = connection.getPeerState();
        peer.setChoked(true);
        sendStateMessage(connection, (byte) 0); // CHOKE message ID = 0
        LOGGER.info("Choking peer: " + peer.getInfo());
    }

    private void unchokePeer(PeerConnection connection) {
        Peer peer = connection.getPeerState();
        peer.setChoked(false);
        sendStateMessage(connection, (byte) 1); // UNCHOKE message ID = 1
        LOGGER.info("Unchoking peer: " + peer.getInfo());
    }

    private void sendStateMessage(PeerConnection connection, byte messageId) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.putInt(1); // Length prefix
        buffer.put(messageId); // Message ID
        buffer.flip();
        
        connection.getWriteQueue().offer(buffer);
        
        SelectionKey key = connection.getSelectionKey();
        if (key != null && key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }
}
