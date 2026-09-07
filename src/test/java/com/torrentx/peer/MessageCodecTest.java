package com.torrentx.peer;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {

    @Test
    void testDecodeKeepAlive() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(0);
        buffer.flip();

        ByteBuffer payload = MessageCodec.decode(buffer);
        assertNotNull(payload);
        assertEquals(0, payload.remaining());
    }

    @Test
    void testDecodeValidMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.putInt(6); // length 6
        buffer.put((byte) 4); // message ID 4
        buffer.put(new byte[]{1, 2, 3, 4, 5}); // 5 bytes payload
        buffer.flip();

        ByteBuffer payload = MessageCodec.decode(buffer);
        assertNotNull(payload);
        assertEquals(6, payload.remaining());
        assertEquals(4, payload.get()); // Check message ID
    }

    @Test
    void testDecodeIncompleteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.putInt(100); // Expecting 100 bytes
        buffer.put(new byte[]{1, 2, 3});
        buffer.flip();

        ByteBuffer payload = MessageCodec.decode(buffer);
        assertNull(payload); // Not enough data
        assertEquals(0, buffer.position()); // Buffer position should be reset
    }

    @Test
    void testDecodeTooLargeMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.putInt(MessageCodec.MAX_MESSAGE_LENGTH + 1);
        buffer.flip();

        assertThrows(IllegalStateException.class, () -> MessageCodec.decode(buffer));
    }
}
