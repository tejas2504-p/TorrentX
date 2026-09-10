package com.torrentx.download;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockTest {

    @Test
    void testValidBlock() {
        Block block = new Block(5, 16384, 16384);
        assertEquals(5, block.getPieceIndex());
        assertEquals(16384, block.getOffset());
        assertEquals(16384, block.getLength());
        assertFalse(block.isRequested());
        assertFalse(block.isReceived());
    }

    @Test
    void testSetState() {
        Block block = new Block(0, 0, 16384);
        block.setRequested(true);
        assertTrue(block.isRequested());
        assertFalse(block.isReceived());

        block.setReceived(true);
        assertTrue(block.isReceived());
    }

    @Test
    void testInvalidIndex() {
        assertThrows(IllegalArgumentException.class, () -> new Block(-1, 0, 16384));
    }

    @Test
    void testInvalidOffset() {
        assertThrows(IllegalArgumentException.class, () -> new Block(0, -1, 16384));
    }

    @Test
    void testInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> new Block(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Block(0, 0, -100));
    }
}
