package com.torrentx.peer;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class PieceMessageTest {

    @Test
    void testValidPieceMessage() {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        PieceMessage msg = new PieceMessage(2, 0, data);
        assertEquals(2, msg.getPieceIndex());
        assertEquals(0, msg.getBlockOffset());
        assertArrayEquals(data, msg.getBlockData());
        assertEquals(PieceMessage.MESSAGE_ID, msg.getMessageId());
    }

    @Test
    void testInvalidPieceIndex() {
        assertThrows(IllegalArgumentException.class, () -> new PieceMessage(-1, 0, new byte[]{1}));
    }

    @Test
    void testInvalidBlockOffset() {
        assertThrows(IllegalArgumentException.class, () -> new PieceMessage(0, -10, new byte[]{1}));
    }

    @Test
    void testEmptyPayload() {
        assertThrows(IllegalArgumentException.class, () -> new PieceMessage(0, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new PieceMessage(0, 0, null));
    }

    @Test
    void testOversizedBlockLength() {
        byte[] largeData = new byte[MessageCodec.MAX_MESSAGE_LENGTH];
        assertThrows(IllegalArgumentException.class, () -> new PieceMessage(0, 0, largeData));
    }

    @Test
    void testRoundTripEncodingDecoding() {
        byte[] data = "Hello BitTorrent Protocol".getBytes();
        PieceMessage original = new PieceMessage(10, 8192, data);
        ByteBuffer encoded = original.toByteBuffer();
        
        // Encoded format: length prefix (4 bytes) + id (1 byte) + piece index (4 bytes) + offset (4 bytes) + data
        assertEquals(4 + 1 + 8 + data.length, encoded.remaining());
        
        int length = encoded.getInt();
        assertEquals(1 + 8 + data.length, length);
        
        byte id = encoded.get();
        assertEquals(PieceMessage.MESSAGE_ID, id);
        
        PieceMessage decoded = PieceMessage.parse(encoded);
        assertEquals(original.getPieceIndex(), decoded.getPieceIndex());
        assertEquals(original.getBlockOffset(), decoded.getBlockOffset());
        assertArrayEquals(original.getBlockData(), decoded.getBlockData());
    }

    @Test
    void testTruncatedPieceMessage() {
        ByteBuffer truncated = ByteBuffer.allocate(4); // Less than 8 bytes required for Piece and Offset
        assertThrows(IllegalArgumentException.class, () -> PieceMessage.parse(truncated));
    }

    @Test
    void testDefensiveCopy() {
        byte[] data = {1, 2, 3};
        PieceMessage msg = new PieceMessage(0, 0, data);
        data[0] = 99;
        assertNotEquals(99, msg.getBlockData()[0]); // Should still be 1

        byte[] returnedData = msg.getBlockData();
        returnedData[0] = 99;
        assertNotEquals(99, msg.getBlockData()[0]); // Should still be 1
    }
}
