package com.torrentx.tracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PeerIdGeneratorTest {

    @BeforeEach
    void setUp() {
        PeerIdGenerator.reset();
    }

    @Test
    void testGenerateFormatAndLength() {
        byte[] peerId = PeerIdGenerator.generate();
        assertNotNull(peerId);
        assertEquals(20, peerId.length);

        String prefix = new String(peerId, 0, 8, StandardCharsets.US_ASCII);
        assertEquals("-TX1000-", prefix);
    }

    @Test
    void testSessionStability() {
        byte[] firstAccess = PeerIdGenerator.getSessionPeerId();
        byte[] secondAccess = PeerIdGenerator.getSessionPeerId();

        assertNotNull(firstAccess);
        assertNotNull(secondAccess);
        assertEquals(20, firstAccess.length);
        assertEquals(20, secondAccess.length);

        // Repeated access should return identical byte data
        assertArrayEquals(firstAccess, secondAccess);
    }

    @Test
    void testResetChangesId() {
        byte[] firstAccess = PeerIdGenerator.getSessionPeerId();
        
        PeerIdGenerator.reset();
        
        byte[] secondAccess = PeerIdGenerator.getSessionPeerId();

        assertNotNull(firstAccess);
        assertNotNull(secondAccess);
        assertEquals(20, firstAccess.length);
        assertEquals(20, secondAccess.length);

        // Verification that a different ID is generated after reset
        boolean matches = true;
        for (int i = 0; i < 20; i++) {
            if (firstAccess[i] != secondAccess[i]) {
                matches = false;
                break;
            }
        }
        assertFalse(matches, "Peer ID should change after resetting the session");
    }

    @Test
    void testSessionGettersProvideClones() {
        byte[] firstAccess = PeerIdGenerator.getSessionPeerId();
        
        // Mutate the returned array copy
        firstAccess[0] = (byte) 'X';

        // Retrieve again and verify cache remained unmutated
        byte[] secondAccess = PeerIdGenerator.getSessionPeerId();
        assertEquals((byte) '-', secondAccess[0]);
    }
}
