package com.torrentx.peer;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class RequestMessageTest {

    @Test
    void testValidRequestMessage() {
        RequestMessage msg = new RequestMessage(1, 16384, 8192);
        assertEquals(1, msg.getPieceIndex());
        assertEquals(16384, msg.getBlockOffset());
        assertEquals(8192, msg.getBlockLength());
        assertEquals(RequestMessage.MESSAGE_ID, msg.getMessageId());
    }

    @Test
    void testInvalidPieceIndex() {
        assertThrows(IllegalArgumentException.class, () -> new RequestMessage(-1, 0, 16384));
    }

    @Test
    void testInvalidBlockOffset() {
        assertThrows(IllegalArgumentException.class, () -> new RequestMessage(0, -10, 16384));
    }

    @Test
    void testInvalidBlockLength() {
        assertThrows(IllegalArgumentException.class, () -> new RequestMessage(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RequestMessage(0, 0, -5));
    }

    @Test
    void testOversizedBlockLength() {
        assertThrows(IllegalArgumentException.class, () -> new RequestMessage(0, 0, MessageCodec.MAX_MESSAGE_LENGTH));
    }

    @Test
    void testRoundTripEncodingDecoding() {
        RequestMessage original = new RequestMessage(5, 32768, 16384);
        ByteBuffer encoded = original.toByteBuffer();
        
        // Encoded format: length prefix (4 bytes) + id (1 byte) + payload (12 bytes)
        assertEquals(17, encoded.remaining());
        
        int length = encoded.getInt();
        assertEquals(13, length);
        
        byte id = encoded.get();
        assertEquals(RequestMessage.MESSAGE_ID, id);
        
        RequestMessage decoded = RequestMessage.parse(encoded);
        assertEquals(original.getPieceIndex(), decoded.getPieceIndex());
        assertEquals(original.getBlockOffset(), decoded.getBlockOffset());
        assertEquals(original.getBlockLength(), decoded.getBlockLength());
    }

    @Test
    void testInvalidRequestLength() {
        ByteBuffer tooShort = ByteBuffer.allocate(8);
        assertThrows(IllegalArgumentException.class, () -> RequestMessage.parse(tooShort));

        ByteBuffer tooLong = ByteBuffer.allocate(16);
        assertThrows(IllegalArgumentException.class, () -> RequestMessage.parse(tooLong));
    }
}
