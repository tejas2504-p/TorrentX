package com.torrentx.network;

import java.nio.ByteBuffer;

/**
 * Parses and validates a BitTorrent HAVE message payload.
 */
public class HaveMessage {
    private final int pieceIndex;

    /**
     * Parses a HAVE message.
     * @param msg The raw wire message
     * @param pieceCount The total number of pieces in the torrent
     * @throws ProtocolException if the message is invalid
     */
    public HaveMessage(Message msg, int pieceCount) throws ProtocolException {
        if (msg.getId() != Message.ID_HAVE) {
            throw new IllegalArgumentException("Not a HAVE message");
        }
        
        byte[] payload = msg.getPayload();
        if (payload.length != 4) {
            throw new ProtocolException("Invalid HAVE payload length: " + payload.length);
        }
        
        this.pieceIndex = ByteBuffer.wrap(payload).getInt();
        
        if (this.pieceIndex < 0 || this.pieceIndex >= pieceCount) {
            throw new ProtocolException("Invalid piece index in HAVE message: " + this.pieceIndex + " (piece count: " + pieceCount + ")");
        }
    }

    public int getPieceIndex() {
        return pieceIndex;
    }
}
