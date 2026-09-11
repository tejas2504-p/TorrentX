package com.torrentx.download;

import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of all torrent pieces, tracks block downloads, and assembles piece data.
 * Fully thread-safe.
 */
public class PieceManager {
    private static final int MAX_ACTIVE_PIECE_BUFFERS = 50;

    private final TorrentLayout layout;
    private final BitSet completedPieces;
    
    // Map to hold in-memory buffers of pieces currently being downloaded
    private final ConcurrentHashMap<Integer, byte[]> activePieceBuffers;
    
    // Map to hold in-memory buffers of pieces that are completely verified (pending storage integration)
    private final ConcurrentHashMap<Integer, byte[]> completedPiecesData;

    public PieceManager(TorrentLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("Layout cannot be null");
        }
        this.layout = layout;
        this.completedPieces = new BitSet(layout.getTotalPieces());
        this.activePieceBuffers = new ConcurrentHashMap<>();
        this.completedPiecesData = new ConcurrentHashMap<>();
    }

    public TorrentLayout getLayout() {
        return layout;
    }
    
    public synchronized boolean isPieceComplete(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= layout.getTotalPieces()) {
            throw new IllegalArgumentException("Invalid piece index");
        }
        return completedPieces.get(pieceIndex);
    }
    
    public void markBlockRequested(int pieceIndex, int offset, int length) {
        Piece piece = layout.getPiece(pieceIndex);
        synchronized (this) {
            if (completedPieces.get(pieceIndex)) {
                throw new IllegalStateException("Piece is already completed and verified");
            }
        }
        piece.markBlockRequested(offset, length);
    }
    
    /**
     * Marks a block as received and writes its data to the buffer.
     * @return true if this block caused the piece to finish downloading and enter the VERIFYING state.
     */
    public synchronized boolean markBlockReceived(int pieceIndex, int offset, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        
        Piece piece = layout.getPiece(pieceIndex);
        if (completedPieces.get(pieceIndex) || piece.getState() == PieceState.VERIFYING || piece.getState() == PieceState.VERIFIED) {
            // Already verified or currently verifying (blocks might be late duplicate arrivals)
            return false; 
        }
        
        // Prevent receiving a block that was never requested or has invalid offset/length
        boolean wasRequested = false;
        int expectedLength = -1;
        for (Block b : piece.getBlocks()) {
            if (b.getOffset() == offset) {
                wasRequested = b.isRequested();
                expectedLength = b.getLength();
                break;
            }
        }
        
        if (expectedLength == -1) {
            throw new IllegalArgumentException("Invalid block offset: " + offset);
        }
        if (expectedLength != data.length) {
            throw new IllegalArgumentException("Invalid data length. Expected " + expectedLength + " but got " + data.length);
        }
        if (!wasRequested) {
            throw new IllegalStateException("Block was never requested");
        }
        
        // Check if we are exceeding the active buffer limit
        if (!activePieceBuffers.containsKey(pieceIndex) && activePieceBuffers.size() >= MAX_ACTIVE_PIECE_BUFFERS) {
            throw new IllegalStateException("Exceeded maximum number of concurrent active piece buffers (" + MAX_ACTIVE_PIECE_BUFFERS + ")");
        }
        
        // Allocate buffer if this is the first block we are receiving for this piece
        byte[] buffer = activePieceBuffers.computeIfAbsent(pieceIndex, k -> new byte[piece.getLength()]);
        
        // Write the data to the buffer
        System.arraycopy(data, 0, buffer, offset, data.length);
        
        // Mark the block received in the state model
        boolean complete = piece.markBlockReceived(offset, data.length);
        if (complete) {
            piece.setState(PieceState.VERIFYING);
        }
        return complete;
    }
    
    /**
     * Assembles and returns the full byte array of a completed piece in VERIFYING state.
     */
    public synchronized byte[] getAssembledPiece(int pieceIndex) {
        Piece piece = layout.getPiece(pieceIndex);
        if (piece.getState() != PieceState.VERIFYING) {
            throw new IllegalStateException("Piece is not in VERIFYING state. Current state: " + piece.getState());
        }
        byte[] buffer = activePieceBuffers.get(pieceIndex);
        if (buffer == null) {
            throw new IllegalStateException("Piece buffer missing from memory");
        }
        return buffer.clone(); // Defensive copy
    }
    
    /**
     * Called after the assembled piece passes SHA-1 verification.
     */
    public synchronized void markPieceVerified(int pieceIndex) {
        Piece piece = layout.getPiece(pieceIndex);
        if (piece.getState() != PieceState.VERIFYING) {
            throw new IllegalStateException("Piece must be in VERIFYING state to be verified");
        }
        piece.setState(PieceState.VERIFIED);
        completedPieces.set(pieceIndex);
        
        // Move memory buffer to completed pieces now that verification is done
        byte[] verifiedData = activePieceBuffers.remove(pieceIndex);
        if (verifiedData != null) {
            completedPiecesData.put(pieceIndex, verifiedData);
        }
    }
    
    /**
     * Called after the assembled piece fails SHA-1 verification.
     */
    public synchronized void markPieceFailed(int pieceIndex) {
        Piece piece = layout.getPiece(pieceIndex);
        if (piece.getState() != PieceState.VERIFYING) {
            throw new IllegalStateException("Piece must be in VERIFYING state to fail");
        }
        piece.reset();
        piece.setState(PieceState.FAILED);
        
        // Free memory buffer so it can be re-downloaded
        activePieceBuffers.remove(pieceIndex);
    }
    
    /**
     * Completely resets a piece, useful if a connection drops and we want to purge partial progress.
     */
    public synchronized void resetPiece(int pieceIndex) {
        Piece piece = layout.getPiece(pieceIndex);
        piece.reset();
        completedPieces.clear(pieceIndex);
        activePieceBuffers.remove(pieceIndex);
    }
    
    /**
     * Resets the requested status of a specific block (e.g. after a timeout).
     */
    public void resetBlockRequested(int pieceIndex, int offset) {
        Piece piece = layout.getPiece(pieceIndex);
        piece.resetBlockRequested(offset);
    }
    
    /**
     * Retrieve verified piece data for testing or storage integration.
     */
    public synchronized byte[] getCompletedPieceData(int pieceIndex) {
        return completedPiecesData.get(pieceIndex);
    }
}
