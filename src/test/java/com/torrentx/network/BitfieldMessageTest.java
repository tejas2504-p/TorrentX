package com.torrentx.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitfieldMessageTest {

    @Test
    void testValidBitfield() throws ProtocolException {
        // pieceCount = 9, so length = 2 bytes
        // byte 0: 10101010 (0xAA) - indices 0, 2, 4, 6
        // byte 1: 10000000 (0x80) - index 8. Spare bits (7 lowest bits) are 0
        byte[] payload = {(byte) 0xAA, (byte) 0x80};
        Message msg = Message.bitfield(payload);
        
        BitfieldMessage bitfield = new BitfieldMessage(msg, 9);
        
        assertTrue(bitfield.hasPiece(0));
        assertFalse(bitfield.hasPiece(1));
        assertTrue(bitfield.hasPiece(2));
        assertFalse(bitfield.hasPiece(3));
        assertTrue(bitfield.hasPiece(4));
        assertFalse(bitfield.hasPiece(5));
        assertTrue(bitfield.hasPiece(6));
        assertFalse(bitfield.hasPiece(7));
        assertTrue(bitfield.hasPiece(8)); // High bit of second byte
        
        assertArrayEquals(payload, bitfield.getRawBitfield());
    }

    @Test
    void testEmptyBitfieldAllowed() throws ProtocolException {
        Message msg = Message.bitfield(new byte[0]);
        BitfieldMessage bitfield = new BitfieldMessage(msg, 9);
        
        assertFalse(bitfield.hasPiece(0));
        assertFalse(bitfield.hasPiece(8));
        assertEquals(0, bitfield.getRawBitfield().length);
    }

    @Test
    void testInvalidLength() {
        // pieceCount = 9 requires 2 bytes. We pass 3 bytes.
        Message msg = Message.bitfield(new byte[]{0, 0, 0});
        assertThrows(ProtocolException.class, () -> new BitfieldMessage(msg, 9));
    }

    @Test
    void testInvalidSpareBits() {
        // pieceCount = 9, length = 2 bytes.
        // byte 1's lowest 7 bits must be 0.
        // Let's set the lowest bit to 1.
        byte[] payload = {(byte) 0x00, (byte) 0x01}; 
        Message msg = Message.bitfield(payload);
        
        ProtocolException ex = assertThrows(ProtocolException.class, () -> new BitfieldMessage(msg, 9));
        assertTrue(ex.getMessage().contains("Spare bits"));
    }

    @Test
    void testInvalidMessageType() {
        Message msg = Message.choke();
        assertThrows(IllegalArgumentException.class, () -> new BitfieldMessage(msg, 9));
    }

    @Test
    void testNegativePieceCount() {
        Message msg = Message.bitfield(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> new BitfieldMessage(msg, -1));
    }

    @Test
    void testOutOfBoundsHasPiece() throws ProtocolException {
        Message msg = Message.bitfield(new byte[]{(byte) 0x80}); // 1 byte
        BitfieldMessage bitfield = new BitfieldMessage(msg, 8); // pieceCount 8
        
        assertThrows(IndexOutOfBoundsException.class, () -> bitfield.hasPiece(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bitfield.hasPiece(8));
    }
}
