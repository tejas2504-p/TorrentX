package com.torrentx.tracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeerInfoTest {

    @Test
    void testPeerInfoCreationAndGetters() {
        String ip = "192.168.1.50";
        int port = 6881;
        byte[] peerId = new byte[20];
        peerId[0] = 9;

        PeerInfo peerInfo = new PeerInfo(ip, port, peerId);

        assertEquals(ip, peerInfo.getIp());
        assertEquals(port, peerInfo.getPort());
        assertArrayEquals(peerId, peerInfo.getPeerId());
    }
}
