package com.torrentx.download;

/**
 * Represents a block (a sub-piece) of a torrent download.
 * Block metadata is immutable. State (requested/received) is thread-safe.
 */
public class Block {
    private final int pieceIndex;
    private final int offset;
    private final int length;
    private volatile boolean requested;
    private volatile boolean received;

    public Block(int pieceIndex, int offset, int length) {
        if (pieceIndex < 0) {
            throw new IllegalArgumentException("Piece index must be non-negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.requested = false;
        this.received = false;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public boolean isRequested() {
        return requested;
    }

    public void setRequested(boolean requested) {
        this.requested = requested;
    }

    public boolean isReceived() {
        return received;
    }

    public void setReceived(boolean received) {
        this.received = received;
    }

    @Override
    public String toString() {
        return "Block{" +
                "pieceIndex=" + pieceIndex +
                ", offset=" + offset +
                ", length=" + length +
                ", requested=" + requested +
                ", received=" + received +
                '}';
    }
}
