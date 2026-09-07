package com.torrentx.peer;

import java.nio.ByteBuffer;

public class MessageCodec {
    public static final int MAX_MESSAGE_LENGTH = 16393; // 16KB block + 9 bytes prefix

    /**
     * Attempts to parse a length-prefixed message from the buffer.
     * @param buffer The input buffer.
     * @return the payload buffer (including message ID), or null if not enough bytes are available.
     * @throws IllegalStateException if the length prefix exceeds the max allowed length.
     */
    public static ByteBuffer decode(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return null; // Need at least the 4-byte length prefix
        }

        buffer.mark();
        int length = buffer.getInt();

        if (length < 0 || length > MAX_MESSAGE_LENGTH) {
            throw new IllegalStateException("Invalid message length: " + length);
        }

        if (length == 0) {
            // Keep-alive message
            return ByteBuffer.allocate(0);
        }

        if (buffer.remaining() < length) {
            buffer.reset(); // Wait for more data
            return null;
        }

        // We have the full message payload
        ByteBuffer payload = buffer.slice();
        payload.limit(length);
        
        // Advance the original buffer position past this message
        buffer.position(buffer.position() + length);

        return payload;
    }
}
