package com.torrentx.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testKeepAlive() {
        Message message = Message.keepAlive();
        assertTrue(message.isKeepAlive());
    }

    @Test
    void testChoke() {
        Message message = Message.choke();
        assertFalse(message.isKeepAlive());
        assertEquals(Message.ID_CHOKE, message.getId());
        assertEquals(0, message.getPayload().length);
    }

    @Test
    void testUnchoke() {
        Message message = Message.unchoke();
        assertFalse(message.isKeepAlive());
        assertEquals(Message.ID_UNCHOKE, message.getId());
        assertEquals(0, message.getPayload().length);
    }

    @Test
    void testInterested() {
        Message message = Message.interested();
        assertFalse(message.isKeepAlive());
        assertEquals(Message.ID_INTERESTED, message.getId());
        assertEquals(0, message.getPayload().length);
    }

    @Test
    void testNotInterested() {
        Message message = Message.notInterested();
        assertFalse(message.isKeepAlive());
        assertEquals(Message.ID_NOT_INTERESTED, message.getId());
        assertEquals(0, message.getPayload().length);
    }

    @Test
    void testInvalidPayloadLengthForChoke() {
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.ID_CHOKE, new byte[]{1}));
    }

    @Test
    void testInvalidPayloadLengthForUnchoke() {
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.ID_UNCHOKE, new byte[]{1}));
    }

    @Test
    void testInvalidPayloadLengthForInterested() {
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.ID_INTERESTED, new byte[]{1}));
    }

    @Test
    void testInvalidPayloadLengthForNotInterested() {
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.ID_NOT_INTERESTED, new byte[]{1}));
    }

    @Test
    void testNullPayloadDefaultsToEmptyArray() {
        // ID 5 is not currently validated for length in this phase
        Message message = new Message((byte) 5, null);
        assertNotNull(message.getPayload());
        assertEquals(0, message.getPayload().length);
    }

    @Test
    void testDefensiveCopying() {
        byte[] payload = new byte[]{1, 2, 3};
        Message message = new Message((byte) 5, payload);
        
        // Mutate original array
        payload[0] = 99;
        assertNotEquals((byte) 99, message.getPayload()[0]);
        
        // Mutate array returned by getter
        byte[] retrieved = message.getPayload();
        retrieved[0] = 55;
        assertNotEquals((byte) 55, message.getPayload()[0]);
    }
}
