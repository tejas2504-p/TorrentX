package com.torrentx.download;

import com.torrentx.tracker.PeerInfo;

import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Tracks piece availability across all connected peers.
 * Fully thread-safe.
 */
public class PieceAvailability {
    private final int totalPieces;
    
    // Maps a peer to the set of pieces they currently possess
    private final ConcurrentHashMap<PeerInfo, BitSet> peerPieces;
    
    // Tracks the total number of peers possessing each piece (for rarest-first calculations)
    private final AtomicIntegerArray pieceFrequencies;

    public PieceAvailability(int totalPieces) {
        if (totalPieces <= 0) {
            throw new IllegalArgumentException("Total pieces must be positive");
        }
        this.totalPieces = totalPieces;
        this.peerPieces = new ConcurrentHashMap<>();
        this.pieceFrequencies = new AtomicIntegerArray(totalPieces);
    }
    
    /**
     * Processes a BITFIELD message from a peer.
     * Overwrites any existing state for this peer (used immediately after handshake).
     */
    public synchronized void processBitfield(PeerInfo peer, byte[] bitfield) {
        if (peer == null || bitfield == null) {
            throw new IllegalArgumentException("Peer and bitfield cannot be null");
        }
        
        // Validate bitfield length matches exactly the required bytes
        int expectedBytes = (totalPieces + 7) / 8;
        if (bitfield.length != expectedBytes) {
            throw new IllegalArgumentException("Invalid bitfield length. Expected " + expectedBytes + " but got " + bitfield.length);
        }
        
        // Validate that trailing unused bits in the final byte are set to 0
        if (totalPieces % 8 != 0) {
            int unusedBits = 8 - (totalPieces % 8);
            byte lastByte = bitfield[bitfield.length - 1];
            int mask = (1 << unusedBits) - 1;
            if ((lastByte & mask) != 0) {
                throw new IllegalArgumentException("Invalid bitfield: unused trailing bits must be zero");
            }
        }
        
        BitSet peerSet = new BitSet(totalPieces);
        for (int i = 0; i < totalPieces; i++) {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8); // Highest bit is piece 0
            if ((bitfield[byteIndex] & (1 << bitIndex)) != 0) {
                peerSet.set(i);
            }
        }
        
        // Handle replacement (if peer sends bitfield twice or we re-use peer object)
        BitSet oldSet = peerPieces.put(peer, peerSet);
        if (oldSet != null) {
            for (int i = 0; i < totalPieces; i++) {
                if (oldSet.get(i)) {
                    pieceFrequencies.decrementAndGet(i);
                }
            }
        }
        
        // Register new frequencies
        for (int i = 0; i < totalPieces; i++) {
            if (peerSet.get(i)) {
                pieceFrequencies.incrementAndGet(i);
            }
        }
    }
    
    /**
     * Processes a HAVE message indicating a peer just completed a piece.
     */
    public synchronized void processHave(PeerInfo peer, int pieceIndex) {
        if (peer == null) {
            throw new IllegalArgumentException("Peer cannot be null");
        }
        if (pieceIndex < 0 || pieceIndex >= totalPieces) {
            throw new IllegalArgumentException("Invalid piece index for HAVE message: " + pieceIndex);
        }
        
        BitSet peerSet = peerPieces.computeIfAbsent(peer, k -> new BitSet(totalPieces));
        
        // Ensure we don't double-count if the peer sends a duplicate HAVE
        if (!peerSet.get(pieceIndex)) {
            peerSet.set(pieceIndex);
            pieceFrequencies.incrementAndGet(pieceIndex);
        }
    }
    
    /**
     * Removes a peer and decrements piece availability frequencies.
     * Called when a peer disconnects.
     */
    public synchronized void removePeer(PeerInfo peer) {
        if (peer == null) return;
        
        BitSet oldSet = peerPieces.remove(peer);
        if (oldSet != null) {
            for (int i = 0; i < totalPieces; i++) {
                if (oldSet.get(i)) {
                    pieceFrequencies.decrementAndGet(i);
                }
            }
        }
    }
    
    /**
     * Checks if a specific peer has a specific piece.
     */
    public boolean peerHasPiece(PeerInfo peer, int pieceIndex) {
        if (peer == null || pieceIndex < 0 || pieceIndex >= totalPieces) return false;
        BitSet peerSet = peerPieces.get(peer);
        return peerSet != null && peerSet.get(pieceIndex);
    }
    
    /**
     * Returns the global frequency (rarity count) of a piece across all connected peers.
     */
    public int getPieceFrequency(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= totalPieces) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }
        return pieceFrequencies.get(pieceIndex);
    }
}
