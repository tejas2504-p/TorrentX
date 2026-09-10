package com.torrentx.download;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PieceTest {

    private byte[] expectedHash;

    @BeforeEach
    void setUp() {
        expectedHash = new byte[20];
        Arrays.fill(expectedHash, (byte) 1);
    }

    @Test
    void testNormalPieceInitialization() {
        // 256KB piece, 16KB blocks -> exactly 16 blocks
        Piece piece = new Piece(0, 262144, expectedHash, 16384);
        
        assertEquals(0, piece.getIndex());
        assertEquals(262144, piece.getLength());
        assertArrayEquals(expectedHash, piece.getExpectedHash());
        assertEquals(PieceState.MISSING, piece.getState());
        
        List<Block> blocks = piece.getBlocks();
        assertEquals(16, blocks.size());
        
        for (int i = 0; i < 16; i++) {
            Block b = blocks.get(i);
            assertEquals(0, b.getPieceIndex());
            assertEquals(i * 16384, b.getOffset());
            assertEquals(16384, b.getLength());
            assertFalse(b.isRequested());
            assertFalse(b.isReceived());
        }
    }

    @Test
    void testFinalPieceWithSmallerLastBlock() {
        // Piece length 30000, block size 16384
        // Block 1: 0, 16384
        // Block 2: 16384, 13616
        Piece piece = new Piece(10, 30000, expectedHash, 16384);
        List<Block> blocks = piece.getBlocks();
        
        assertEquals(2, blocks.size());
        
        Block b1 = blocks.get(0);
        assertEquals(16384, b1.getLength());
        assertEquals(0, b1.getOffset());
        
        Block b2 = blocks.get(1);
        assertEquals(13616, b2.getLength());
        assertEquals(16384, b2.getOffset());
    }

    @Test
    void testDefensiveCopying() {
        Piece piece = new Piece(0, 16384, expectedHash, 16384);
        
        // Mutate original hash
        expectedHash[0] = 99;
        assertNotEquals(99, piece.getExpectedHash()[0]);
        
        // Mutate returned hash
        byte[] returnedHash = piece.getExpectedHash();
        returnedHash[0] = 88;
        assertNotEquals(88, piece.getExpectedHash()[0]);
    }

    @Test
    void testBlockCompletionAndReset() {
        Piece piece = new Piece(0, 32768, expectedHash, 16384);
        assertFalse(piece.isComplete());
        
        // Mark first block requested and received
        piece.markBlockRequested(0, 16384);
        assertEquals(PieceState.DOWNLOADING, piece.getState());
        assertFalse(piece.markBlockReceived(0, 16384));
        assertFalse(piece.isComplete());
        
        // Mark second block received
        assertTrue(piece.markBlockReceived(16384, 16384));
        assertTrue(piece.isComplete());
        
        // Reset
        piece.reset();
        assertFalse(piece.isComplete());
        assertEquals(PieceState.MISSING, piece.getState());
        assertFalse(piece.getBlocks().get(0).isReceived());
    }

    @Test
    void testInvalidBlockMarking() {
        Piece piece = new Piece(0, 16384, expectedHash, 16384);
        
        // Invalid offset
        assertThrows(IllegalArgumentException.class, () -> piece.markBlockRequested(1, 16384));
        
        // Invalid length
        assertThrows(IllegalArgumentException.class, () -> piece.markBlockReceived(0, 1000));
    }

    @Test
    void testInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new Piece(-1, 16384, expectedHash, 16384));
        assertThrows(IllegalArgumentException.class, () -> new Piece(0, 0, expectedHash, 16384));
        assertThrows(IllegalArgumentException.class, () -> new Piece(0, 16384, new byte[19], 16384));
        assertThrows(IllegalArgumentException.class, () -> new Piece(0, 16384, null, 16384));
        assertThrows(IllegalArgumentException.class, () -> new Piece(0, 16384, expectedHash, 0));
    }
}
