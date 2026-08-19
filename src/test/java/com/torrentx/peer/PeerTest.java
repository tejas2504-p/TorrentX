package com.torrentx.peer;

import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeerTest {

    private PeerInfo peerInfo;

    @BeforeEach
    void setUp() {
        byte[] id = new byte[20];
        id[1] = 8;
        peerInfo = new PeerInfo("10.0.0.1", 6882, id);
    }

    @Test
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Peer(null));
    }

    @Test
    void testInitialState() {
        Peer peer = new Peer(peerInfo);
        assertEquals(peerInfo, peer.getInfo());
        assertTrue(peer.isChoked());
        assertFalse(peer.isInterested());
        assertTrue(peer.isChokingMe());
        assertFalse(peer.isInterestedInMe());
    }

    @Test
    void testStateTransitions() {
        Peer peer = new Peer(peerInfo);

        peer.setChoked(false);
        assertFalse(peer.isChoked());

        peer.setInterested(true);
        assertTrue(peer.isInterested());

        peer.setChokingMe(false);
        assertFalse(peer.isChokingMe());

        peer.setInterestedInMe(true);
        assertTrue(peer.isInterestedInMe());
    }
}
