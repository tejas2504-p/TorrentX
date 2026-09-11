package com.torrentx.download;

import com.torrentx.tracker.PeerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the selection and lifecycle of in-flight block requests.
 * Fully thread-safe.
 */
public class BlockSelector {

    private final PieceManager pieceManager;
    private final int maxInFlightRequests;
    
    // Tracks specific blocks currently requested across all peers.
    // Key format: "pieceIndex:offset"
    private final ConcurrentHashMap<String, BlockRequest> inFlightRequests;
    
    // Tracks the count of active pipeline requests assigned to each specific peer
    private final ConcurrentHashMap<PeerInfo, AtomicInteger> peerInFlightCounts;

    public BlockSelector(PieceManager pieceManager, int maxInFlightRequests) {
        if (pieceManager == null) {
            throw new IllegalArgumentException("PieceManager cannot be null");
        }
        if (maxInFlightRequests <= 0) {
            throw new IllegalArgumentException("Max in-flight requests must be positive");
        }
        this.pieceManager = pieceManager;
        this.maxInFlightRequests = maxInFlightRequests;
        this.inFlightRequests = new ConcurrentHashMap<>();
        this.peerInFlightCounts = new ConcurrentHashMap<>();
    }
    
    private String requestKey(int pieceIndex, int offset) {
        return pieceIndex + ":" + offset;
    }

    /**
     * Selects up to a maximum number of unrequested blocks from a piece for the specified peer.
     * Respects the configured maxInFlightRequests per-peer constraint.
     */
    public synchronized List<BlockRequest> selectBlocks(PeerInfo peer, int pieceIndex) {
        if (peer == null) {
            throw new IllegalArgumentException("Peer cannot be null");
        }
        
        List<BlockRequest> newRequests = new ArrayList<>();
        AtomicInteger peerCount = peerInFlightCounts.computeIfAbsent(peer, k -> new AtomicInteger(0));
        
        if (peerCount.get() >= maxInFlightRequests) {
            return newRequests; // Pipeline is already full for this peer
        }
        
        Piece piece = pieceManager.getLayout().getPiece(pieceIndex);
        
        // Synchronize on the piece to safely inspect block request states
        synchronized (piece) {
            if (pieceManager.isPieceComplete(pieceIndex) || piece.getState() == PieceState.VERIFYING || piece.getState() == PieceState.VERIFIED) {
                return newRequests;
            }
            
            for (Block block : piece.getBlocks()) {
                if (!block.isRequested()) {
                    String key = requestKey(pieceIndex, block.getOffset());
                    
                    // Double check it's not somehow in our in-flight map
                    if (!inFlightRequests.containsKey(key)) {
                        pieceManager.markBlockRequested(pieceIndex, block.getOffset(), block.getLength());
                        
                        BlockRequest req = new BlockRequest(pieceIndex, block.getOffset(), block.getLength(), peer);
                        inFlightRequests.put(key, req);
                        newRequests.add(req);
                        
                        if (peerCount.incrementAndGet() >= maxInFlightRequests) {
                            break; // Pipeline max reached
                        }
                    }
                }
            }
        }
        
        return newRequests;
    }
    
    public synchronized boolean markBlockReceived(PeerInfo peer, int pieceIndex, int offset, byte[] data) {
        String key = requestKey(pieceIndex, offset);
        BlockRequest req = inFlightRequests.get(key);
        
        if (req == null) {
            return false; // Unsolicited or timed-out request
        }
        
        if (!req.getPeer().equals(peer)) {
            return false; // Requested by someone else
        }
        
        if (req.getLength() != data.length) {
            return false; // Invalid length for this request
        }
        
        // Validation passed, remove from in-flight
        inFlightRequests.remove(key);
        
        AtomicInteger count = peerInFlightCounts.get(req.getPeer());
        if (count != null) {
            count.decrementAndGet();
        }
        
        try {
            return pieceManager.markBlockReceived(pieceIndex, offset, data);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Already verified length and in-flight status, so this shouldn't normally happen
            return false;
        }
    }
    
    /**
     * Explicitly releases a specific block request (e.g. upon protocol rejection/choke).
     * It allows another peer to request it immediately.
     */
    public synchronized void releaseRequest(int pieceIndex, int offset) {
        String key = requestKey(pieceIndex, offset);
        BlockRequest req = inFlightRequests.remove(key);
        if (req != null) {
            AtomicInteger count = peerInFlightCounts.get(req.getPeer());
            if (count != null) {
                count.decrementAndGet();
            }
            
            pieceManager.resetBlockRequested(pieceIndex, offset);
        }
    }
    
    /**
     * Sweeps and releases all block requests that have exceeded the timeout threshold.
     * @return the number of blocks released.
     */
    public synchronized int releaseTimedOutRequests(long timeoutMillis) {
        int releasedCount = 0;
        long now = System.currentTimeMillis();
        
        List<String> toRelease = new ArrayList<>();
        
        for (java.util.Map.Entry<String, BlockRequest> entry : inFlightRequests.entrySet()) {
            if (now - entry.getValue().getRequestTime() > timeoutMillis) {
                toRelease.add(entry.getKey());
            }
        }
        
        for (String key : toRelease) {
            BlockRequest req = inFlightRequests.get(key);
            releaseRequest(req.getPieceIndex(), req.getOffset());
            releasedCount++;
        }
        
        return releasedCount;
    }

    /**
     * Releases all requests tied to a specific peer, useful when they disconnect.
     */
    public synchronized void releaseAllPeerRequests(PeerInfo peer) {
        if (peer == null) return;
        
        List<String> toRelease = new ArrayList<>();
        
        for (java.util.Map.Entry<String, BlockRequest> entry : inFlightRequests.entrySet()) {
            if (entry.getValue().getPeer().equals(peer)) {
                toRelease.add(entry.getKey());
            }
        }
        
        for (String key : toRelease) {
            BlockRequest req = inFlightRequests.get(key);
            releaseRequest(req.getPieceIndex(), req.getOffset());
        }
        
        peerInFlightCounts.remove(peer);
    }
    
    public int getInFlightCountForPeer(PeerInfo peer) {
        AtomicInteger count = peerInFlightCounts.get(peer);
        return count == null ? 0 : count.get();
    }
}
