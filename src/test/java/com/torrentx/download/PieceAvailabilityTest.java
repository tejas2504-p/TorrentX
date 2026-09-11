package com.torrentx.download;

import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PieceAvailabilityTest {

    private PieceAvailability availability;
    private PeerInfo peer1;
    private PeerInfo peer2;

    @BeforeEach
    void setUp() {
        availability = new PieceAvailability(10); // 10 total pieces
        peer1 = new PeerInfo("127.0.0.1", 6881, new byte[20]);
        peer2 = new PeerInfo("127.0.0.2", 6881, new byte[20]);
    }

    @Test
    void testProcessHave() {
        availability.processHave(peer1, 5);
        availability.processHave(peer1, 2);
        
        assertTrue(availability.peerHasPiece(peer1, 5));
        assertTrue(availability.peerHasPiece(peer1, 2));
        assertFalse(availability.peerHasPiece(peer1, 0));
        
        assertEquals(1, availability.getPieceFrequency(5));
        assertEquals(1, availability.getPieceFrequency(2));
        assertEquals(0, availability.getPieceFrequency(0));
        
        // Duplicate HAVE should not double-count frequency
        availability.processHave(peer1, 5);
        assertEquals(1, availability.getPieceFrequency(5));
    }

    @Test
    void testProcessBitfield() {
        // 10 pieces -> needs 2 bytes.
        // Let's set piece 0, 1, and 8.
        // Byte 0: piece 0 (bit 7), piece 1 (bit 6) -> 11000000 binary -> 192 (or 0xC0)
        // Byte 1: piece 8 (bit 7) -> 10000000 binary -> 128 (or 0x80)
        // Note: Java bytes are signed. 0xC0 is -64, 0x80 is -128.
        byte[] bitfield = new byte[]{(byte) 0xC0, (byte) 0x80};
        
        availability.processBitfield(peer1, bitfield);
        
        assertTrue(availability.peerHasPiece(peer1, 0));
        assertTrue(availability.peerHasPiece(peer1, 1));
        assertTrue(availability.peerHasPiece(peer1, 8));
        assertFalse(availability.peerHasPiece(peer1, 2));
        assertFalse(availability.peerHasPiece(peer1, 9));
        
        assertEquals(1, availability.getPieceFrequency(0));
        assertEquals(1, availability.getPieceFrequency(1));
        assertEquals(1, availability.getPieceFrequency(8));
        assertEquals(0, availability.getPieceFrequency(9));
    }

    @Test
    void testBitfieldInvalidLengths() {
        // 10 pieces requires exactly 2 bytes. 1 byte is too short, 3 is too long.
        assertThrows(IllegalArgumentException.class, () -> availability.processBitfield(peer1, new byte[]{(byte) 0xC0}));
        assertThrows(IllegalArgumentException.class, () -> availability.processBitfield(peer1, new byte[]{(byte) 0xC0, 0, 0}));
    }

    @Test
    void testBitfieldInvalidUnusedBits() {
        // 10 pieces, 2 bytes. Last byte has 6 unused bits.
        // Piece 8 is bit 7, piece 9 is bit 6.
        // Bits 5, 4, 3, 2, 1, 0 MUST be zero.
        // If we set bit 0 (which would be piece 15, but we only have 10 pieces), it should reject.
        byte[] invalidBitfield = new byte[]{(byte) 0xC0, (byte) 0x81}; 
        assertThrows(IllegalArgumentException.class, () -> availability.processBitfield(peer1, invalidBitfield));
    }

    @Test
    void testRemovePeer() {
        availability.processHave(peer1, 5);
        availability.processHave(peer2, 5);
        
        assertEquals(2, availability.getPieceFrequency(5));
        
        availability.removePeer(peer1);
        
        assertEquals(1, availability.getPieceFrequency(5));
        assertFalse(availability.peerHasPiece(peer1, 5));
        assertTrue(availability.peerHasPiece(peer2, 5));
    }

    @Test
    void testBitfieldReplacement() {
        availability.processHave(peer1, 5);
        assertEquals(1, availability.getPieceFrequency(5));
        
        // Processing a bitfield should overwrite previous state
        byte[] bitfield = new byte[]{(byte) 0x00, (byte) 0x00}; // Peer has nothing
        availability.processBitfield(peer1, bitfield);
        
        assertEquals(0, availability.getPieceFrequency(5));
        assertFalse(availability.peerHasPiece(peer1, 5));
    }

    @Test
    void testInvalidHaveIndex() {
        assertThrows(IllegalArgumentException.class, () -> availability.processHave(peer1, -1));
        assertThrows(IllegalArgumentException.class, () -> availability.processHave(peer1, 10)); // Total is 10, max index is 9
    }
}
