package com.torrentx.network;

/**
 * Parses and validates a BitTorrent BITFIELD message payload.
 */
public class BitfieldMessage {
    private final byte[] bitfield;
    private final int pieceCount;

    /**
     * Parses a BITFIELD message.
     * @param msg The raw wire message
     * @param pieceCount The total number of pieces in the torrent
     * @throws ProtocolException if the bitfield is invalid
     */
    public BitfieldMessage(Message msg, int pieceCount) throws ProtocolException {
        if (msg.getId() != Message.ID_BITFIELD) {
            throw new IllegalArgumentException("Not a BITFIELD message");
        }
        
        if (pieceCount < 0) {
            throw new IllegalArgumentException("Piece count cannot be negative");
        }
        
        this.bitfield = msg.getPayload();
        this.pieceCount = pieceCount;
        
        int expectedLength = (pieceCount + 7) / 8;
        
        // Some clients send empty bitfield when they have 0 pieces
        if (bitfield.length == 0) {
            return;
        }
        
        if (bitfield.length != expectedLength) {
            throw new ProtocolException("Invalid bitfield length. Expected: " + expectedLength + ", Actual: " + bitfield.length);
        }
        
        // Validate spare bits (unused bits at the end) are strictly zero
        int spareBits = (expectedLength * 8) - pieceCount;
        if (spareBits > 0) {
            byte lastByte = bitfield[bitfield.length - 1];
            int mask = (1 << spareBits) - 1; // Creates a mask for the 'spareBits' number of lowest bits
            if ((lastByte & mask) != 0) {
                throw new ProtocolException("Spare bits in bitfield must be zero");
            }
        }
    }

    /**
     * Checks if a piece is available.
     * @param index piece index
     * @return true if the piece is available
     */
    public boolean hasPiece(int index) {
        if (index < 0 || index >= pieceCount) {
            throw new IndexOutOfBoundsException("Invalid piece index: " + index);
        }
        
        if (bitfield.length == 0) {
            return false;
        }
        
        int byteIndex = index / 8;
        int bitIndex = 7 - (index % 8); // High bit first
        
        return ((bitfield[byteIndex] >> bitIndex) & 1) != 0;
    }
    
    /**
     * Returns a copy of the raw bitfield bytes.
     */
    public byte[] getRawBitfield() {
        return bitfield.clone();
    }
}
