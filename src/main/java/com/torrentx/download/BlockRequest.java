package com.torrentx.download;

import com.torrentx.tracker.PeerInfo;

/**
 * Represents an in-flight request for a block assigned to a specific peer.
 * Immutable object.
 */
public class BlockRequest {
    private final int pieceIndex;
    private final int offset;
    private final int length;
    private final PeerInfo peer;
    private final long requestTime;

    public BlockRequest(int pieceIndex, int offset, int length, PeerInfo peer) {
        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new IllegalArgumentException("Invalid block dimensions");
        }
        if (peer == null) {
            throw new IllegalArgumentException("Peer cannot be null");
        }
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.peer = peer;
        this.requestTime = System.currentTimeMillis();
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

    public PeerInfo getPeer() {
        return peer;
    }

    public long getRequestTime() {
        return requestTime;
    }
}
