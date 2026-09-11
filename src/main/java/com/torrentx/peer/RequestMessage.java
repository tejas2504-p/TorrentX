package com.torrentx.peer;

import java.nio.ByteBuffer;

public class RequestMessage implements PeerMessage {
    public static final int MESSAGE_ID = 6;
    private static final int PAYLOAD_LENGTH = 13; // 1 byte ID + 12 bytes data

    private final int pieceIndex;
    private final int blockOffset;
    private final int blockLength;

    public RequestMessage(int pieceIndex, int blockOffset, int blockLength) {
        if (pieceIndex < 0) {
            throw new IllegalArgumentException("Piece index cannot be negative");
        }
        if (blockOffset < 0) {
            throw new IllegalArgumentException("Block offset cannot be negative");
        }
        if (blockLength <= 0 || blockLength > MessageCodec.MAX_MESSAGE_LENGTH - 9) {
            throw new IllegalArgumentException("Invalid block length: " + blockLength);
        }
        this.pieceIndex = pieceIndex;
        this.blockOffset = blockOffset;
        this.blockLength = blockLength;
    }

    public int getPieceIndex() { return pieceIndex; }
    public int getBlockOffset() { return blockOffset; }
    public int getBlockLength() { return blockLength; }

    @Override
    public int getMessageId() {
        return MESSAGE_ID;
    }

    @Override
    public ByteBuffer toByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + PAYLOAD_LENGTH);
        buffer.putInt(PAYLOAD_LENGTH);
        buffer.put((byte) MESSAGE_ID);
        buffer.putInt(pieceIndex);
        buffer.putInt(blockOffset);
        buffer.putInt(blockLength);
        buffer.flip();
        return buffer;
    }

    /**
     * Parses a Request message payload.
     * Note: The message ID byte must have already been consumed from the buffer.
     */
    public static RequestMessage parse(ByteBuffer payload) {
        if (payload.remaining() != 12) {
            throw new IllegalArgumentException("Request message must have exactly 12 bytes of payload");
        }
        int pieceIndex = payload.getInt();
        int blockOffset = payload.getInt();
        int blockLength = payload.getInt();
        return new RequestMessage(pieceIndex, blockOffset, blockLength);
    }
}
