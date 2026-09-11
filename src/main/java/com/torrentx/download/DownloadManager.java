package com.torrentx.download;

import com.torrentx.peer.Peer;
import com.torrentx.peer.PeerConnection;
import com.torrentx.peer.PeerManager;
import com.torrentx.peer.PeerConnectionState;
import com.torrentx.peer.RequestMessage;
import com.torrentx.tracker.PeerInfo;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DownloadManager implements PieceCompletionListener, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DownloadManager.class.getName());
    
    private final PeerManager peerManager;
    private final PieceManager pieceManager;
    private final PieceSelector pieceSelector;
    private final BlockSelector blockSelector;
    private final PieceAvailability pieceAvailability;
    private final PieceAssembler pieceAssembler;
    
    private final ScheduledExecutorService scheduler;
    private volatile boolean running;

    public DownloadManager(PeerManager peerManager, PieceManager pieceManager, 
                           PieceSelector pieceSelector, BlockSelector blockSelector, 
                           PieceAvailability pieceAvailability, PieceAssembler pieceAssembler) {
        if (peerManager == null || pieceManager == null || pieceSelector == null ||
            blockSelector == null || pieceAvailability == null || pieceAssembler == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.peerManager = peerManager;
        this.pieceManager = pieceManager;
        this.pieceSelector = pieceSelector;
        this.blockSelector = blockSelector;
        this.pieceAvailability = pieceAvailability;
        this.pieceAssembler = pieceAssembler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.peerManager.setPieceCompletionListener(this);
    }
    
    public void start() {
        if (running) return;
        running = true;
        // Schedule the main download loop
        scheduler.scheduleAtFixedRate(this::downloadTask, 0, 100, TimeUnit.MILLISECONDS);
        // Schedule timeout sweeps
        scheduler.scheduleAtFixedRate(this::timeoutTask, 5, 5, TimeUnit.SECONDS);
    }
    
    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
    }
    
    private void downloadTask() {
        if (!running) return;
        
        for (PeerConnection connection : peerManager.getConnectedPeers()) {
            if (connection.getState() != PeerConnectionState.READY) {
                continue;
            }
            
            // Check if we should be interested
            checkInterested(connection);
            
            if (connection.getPeerState().isChokingMe()) {
                continue; // Cannot request blocks while choked
            }
            
            // We are unchoked, try to request blocks
            requestBlocksFromPeer(connection);
        }
    }
    
    private void checkInterested(PeerConnection connection) {
        PeerInfo peerInfo = connection.getPeerInfo();
        Peer peer = connection.getPeerState();
        boolean shouldBeInterested = false;
        
        // Check if this peer has ANY piece we don't have
        for (int i = 0; i < pieceManager.getLayout().getTotalPieces(); i++) {
            if (!pieceManager.isPieceComplete(i) && pieceAvailability.peerHasPiece(peerInfo, i)) {
                shouldBeInterested = true;
                break;
            }
        }
        
        if (shouldBeInterested && !peer.isInterested()) {
            peer.setInterested(true);
            // Send INTERESTED message (ID=2)
            ByteBuffer buffer = ByteBuffer.allocate(5);
            buffer.putInt(1);
            buffer.put((byte) 2);
            buffer.flip();
            connection.getWriteQueue().offer(buffer);
            if (connection.getSelectionKey() != null && connection.getSelectionKey().isValid()) {
                connection.getSelectionKey().interestOps(
                    connection.getSelectionKey().interestOps() | java.nio.channels.SelectionKey.OP_WRITE
                );
            }
        } else if (!shouldBeInterested && peer.isInterested()) {
            peer.setInterested(false);
            // Send NOT INTERESTED message (ID=3)
            ByteBuffer buffer = ByteBuffer.allocate(5);
            buffer.putInt(1);
            buffer.put((byte) 3);
            buffer.flip();
            connection.getWriteQueue().offer(buffer);
            if (connection.getSelectionKey() != null && connection.getSelectionKey().isValid()) {
                connection.getSelectionKey().interestOps(
                    connection.getSelectionKey().interestOps() | java.nio.channels.SelectionKey.OP_WRITE
                );
            }
        }
    }
    
    private void requestBlocksFromPeer(PeerConnection connection) {
        PeerInfo peerInfo = connection.getPeerInfo();
        
        int pieceIndex = pieceSelector.selectNextPiece(peerInfo, pieceManager, pieceAvailability);
        if (pieceIndex == -1) {
            return; // No piece to request from this peer
        }
        
        List<BlockRequest> requests = blockSelector.selectBlocks(peerInfo, pieceIndex);
        for (BlockRequest req : requests) {
            RequestMessage reqMsg = new RequestMessage(req.getPieceIndex(), req.getOffset(), req.getLength());
            connection.getWriteQueue().offer(reqMsg.toByteBuffer());
        }
        
        if (!requests.isEmpty() && connection.getSelectionKey() != null && connection.getSelectionKey().isValid()) {
            connection.getSelectionKey().interestOps(
                connection.getSelectionKey().interestOps() | java.nio.channels.SelectionKey.OP_WRITE
            );
        }
    }
    
    private void timeoutTask() {
        if (!running) return;
        int released = blockSelector.releaseTimedOutRequests(10000); // 10 second timeout
        if (released > 0) {
            LOGGER.info("Released " + released + " timed out block requests");
        }
    }
    
    @Override
    public void onPieceCompleted(int pieceIndex) {
        // Run piece assembly & verification async so we don't block the network reactor
        scheduler.execute(() -> {
            boolean valid = pieceAssembler.verifyPiece(pieceIndex);
            if (valid) {
                LOGGER.info("Piece " + pieceIndex + " successfully verified!");
                // Future phase: Write to disk
            } else {
                LOGGER.warning("Piece " + pieceIndex + " failed verification!");
            }
        });
    }
}
