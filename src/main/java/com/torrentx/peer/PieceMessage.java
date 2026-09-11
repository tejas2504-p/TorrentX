package com.torrentx.peer;

import java.nio.ByteBuffer;

public class PieceMessage implements PeerMessage {
    public static final int MESSAGE_ID = 7;

    private final int pieceIndex;
    private final int blockOffset;
    private final byte[] blockData;

    public PieceMessage(int pieceIndex, int blockOffset, byte[] blockData) {
        if (pieceIndex < 0) {
            throw new IllegalArgumentException("Piece index cannot be negative");
        }
        if (blockOffset < 0) {
            throw new IllegalArgumentException("Block offset cannot be negative");
        }
        if (blockData == null || blockData.length == 0) {
            throw new IllegalArgumentException("Block data cannot be null or empty");
        }
        if (blockData.length > MessageCodec.MAX_MESSAGE_LENGTH - 9) {
            throw new IllegalArgumentException("Block data exceeds maximum length: " + blockData.length);
        }
        this.pieceIndex = pieceIndex;
        this.blockOffset = blockOffset;
        this.blockData = blockData.clone(); // Defensive copy
    }

    public int getPieceIndex() { return pieceIndex; }
    public int getBlockOffset() { return blockOffset; }
    public byte[] getBlockData() { return blockData.clone(); }

    @Override
    public int getMessageId() {
        return MESSAGE_ID;
    }

    @Override
    public ByteBuffer toByteBuffer() {
        int payloadLength = 1 + 8 + blockData.length; // 1 byte ID + 8 bytes header + data
        ByteBuffer buffer = ByteBuffer.allocate(4 + payloadLength);
        buffer.putInt(payloadLength);
        buffer.put((byte) MESSAGE_ID);
        buffer.putInt(pieceIndex);
        buffer.putInt(blockOffset);
        buffer.put(blockData);
        buffer.flip();
        return buffer;
    }

    /**
     * Parses a Piece message payload.
     * Note: The message ID byte must have already been consumed from the buffer.
     */
    public static PieceMessage parse(ByteBuffer payload) {
        if (payload.remaining() < 8) {
            throw new IllegalArgumentException("Piece message must have at least 8 bytes of payload");
        }
        int pieceIndex = payload.getInt();
        int blockOffset = payload.getInt();
        
        byte[] blockData = new byte[payload.remaining()];
        payload.get(blockData);
        
        return new PieceMessage(pieceIndex, blockOffset, blockData);
    }
}
