package com.torrentx.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testMessageCreationAndGetters() {
        byte id = 2; // interested message type
        byte[] payload = new byte[]{0, 0, 0, 1};

        Message message = new Message(id, payload);

        assertEquals(id, message.getId());
        assertArrayEquals(payload, message.getPayload());
    }

    @Test
    void testMessageWithNullPayload() {
        byte id = 0; // keep-alive message type
        Message message = new Message(id, null);

        assertEquals(id, message.getId());
        assertNull(message.getPayload());
    }
}
