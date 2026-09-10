package com.torrentx.download;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a torrent piece and manages its constituent blocks.
 * Thread-safe implementation for tracking completion status.
 */
public class Piece {
    private final int index;
    private final int length;
    private final byte[] expectedHash;
    private final List<Block> blocks;
    
    private volatile PieceState state;

    public Piece(int index, int length, byte[] expectedHash, int standardBlockSize) {
        if (index < 0) throw new IllegalArgumentException("Index must be non-negative");
        if (length <= 0) throw new IllegalArgumentException("Length must be positive");
        if (expectedHash == null || expectedHash.length != 20) {
            throw new IllegalArgumentException("Expected hash must be exactly 20 bytes");
        }
        if (standardBlockSize <= 0) throw new IllegalArgumentException("Standard block size must be positive");

        this.index = index;
        this.length = length;
        this.expectedHash = expectedHash.clone(); // Defensive copy
        this.state = PieceState.MISSING;

        List<Block> tempBlocks = new ArrayList<>();
        int offset = 0;
        while (offset < length) {
            int blockLength = Math.min(standardBlockSize, length - offset);
            tempBlocks.add(new Block(index, offset, blockLength));
            offset += blockLength;
        }
        this.blocks = Collections.unmodifiableList(tempBlocks);
    }

    public int getIndex() {
        return index;
    }

    public int getLength() {
        return length;
    }

    public byte[] getExpectedHash() {
        return expectedHash.clone(); // Defensive copy
    }

    public PieceState getState() {
        return state;
    }

    public void setState(PieceState state) {
        this.state = state;
    }

    /**
     * Returns an unmodifiable list of the blocks in this piece.
     */
    public List<Block> getBlocks() {
        return blocks;
    }

    /**
     * Returns true if all blocks in this piece have been received.
     */
    public synchronized boolean isComplete() {
        for (Block b : blocks) {
            if (!b.isReceived()) return false;
        }
        return true;
    }

    /**
     * Resets the piece to MISSING state and marks all blocks as not requested/received.
     */
    public synchronized void reset() {
        for (Block b : blocks) {
            b.setRequested(false);
            b.setReceived(false);
        }
        this.state = PieceState.MISSING;
    }

    /**
     * Safely marks a specific block as received.
     * @return true if this block completion caused the entire piece to become complete.
     */
    public synchronized boolean markBlockReceived(int offset, int length) {
        boolean found = false;
        for (Block b : blocks) {
            if (b.getOffset() == offset) {
                if (b.getLength() != length) {
                    throw new IllegalArgumentException("Invalid block length " + length + " for offset " + offset);
                }
                b.setReceived(true);
                found = true;
                break;
            }
        }
        
        if (!found) {
            throw new IllegalArgumentException("Invalid block offset " + offset);
        }
        
        if (state == PieceState.MISSING) {
            state = PieceState.DOWNLOADING;
        }
        
        return isComplete();
    }

    /**
     * Safely marks a specific block as requested.
     */
    public synchronized void markBlockRequested(int offset, int length) {
        boolean found = false;
        for (Block b : blocks) {
            if (b.getOffset() == offset) {
                if (b.getLength() != length) {
                    throw new IllegalArgumentException("Invalid block length " + length + " for offset " + offset);
                }
                b.setRequested(true);
                found = true;
                break;
            }
        }
        
        if (!found) {
            throw new IllegalArgumentException("Invalid block offset " + offset);
        }
        
        if (state == PieceState.MISSING) {
            state = PieceState.DOWNLOADING;
        }
    }
}
