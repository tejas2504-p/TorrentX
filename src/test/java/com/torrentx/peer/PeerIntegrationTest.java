package com.torrentx.peer;

import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PeerIntegrationTest {
    private PeerManager peerManager;
    private byte[] localInfoHash;
    private byte[] localPeerId;
    
    @BeforeEach
    void setUp() throws Exception {
        localInfoHash = new byte[20];
        Arrays.fill(localInfoHash, (byte) 7);
        
        localPeerId = new byte[20];
        Arrays.fill(localPeerId, (byte) 8);
        
        peerManager = new PeerManager(localInfoHash, localPeerId, 50);
        peerManager.start();
    }
    
    @AfterEach
    void tearDown() {
        if (peerManager != null) {
            peerManager.close();
        }
    }

    private Peer waitForPeerState(int port, int timeoutMs) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < endTime) {
            Thread.sleep(50);
            for (PeerConnection pc : peerManager.getConnectedPeers()) {
                Peer p = pc.getPeerState();
                if (p.getInfo().getPort() == port) {
                    return p;
                }
            }
        }
        return null;
    }
    
    private void connectPeer(int port) {
        PeerInfo peerInfo = new PeerInfo("127.0.0.1", port, new byte[20]);
        peerManager.addPeers(Collections.singletonList(peerInfo));
    }

    @Test
    void testKeepAlive() throws Exception {
        try (MockPeerServer server = new MockPeerServer(localInfoHash, session -> {
            try {
                session.expectAndValidateHandshake();
                session.sendHandshake(localInfoHash, new byte[20]);
                session.sendKeepAlive();
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        })) {
            connectPeer(server.getPort());
            assertTrue(server.awaitConnection(2, TimeUnit.SECONDS));
            
            Peer p = waitForPeerState(server.getPort(), 2000);
            assertNotNull(p, "Peer should be connected");
            // Keep alive doesn't alter visible state, just checking we don't crash
        }
    }

    @Test
    void testChokeUnchoke() throws Exception {
        try (MockPeerServer server = new MockPeerServer(localInfoHash, session -> {
            try {
                session.expectAndValidateHandshake();
                session.sendHandshake(localInfoHash, new byte[20]);
                session.sendUnchoke();
                Thread.sleep(500);
                session.sendChoke();
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        })) {
            connectPeer(server.getPort());
            assertTrue(server.awaitConnection(2, TimeUnit.SECONDS));
            
            Peer p = waitForPeerState(server.getPort(), 2000);
            assertNotNull(p, "Peer should be connected");
            
            // Wait for unchoke
            long timeout = System.currentTimeMillis() + 2000;
            while (p.isChokingMe() && System.currentTimeMillis() < timeout) {
                Thread.sleep(50);
            }
            assertFalse(p.isChokingMe(), "Peer should have transitioned to unchoked state");
            
            // Wait for choke
            timeout = System.currentTimeMillis() + 2000;
            while (!p.isChokingMe() && System.currentTimeMillis() < timeout) {
                Thread.sleep(50);
            }
            assertTrue(p.isChokingMe(), "Peer should have transitioned to choked state");
        }
    }

    @Test
    void testInterestedNotInterested() throws Exception {
        try (MockPeerServer server = new MockPeerServer(localInfoHash, session -> {
            try {
                session.expectAndValidateHandshake();
                session.sendHandshake(localInfoHash, new byte[20]);
                session.sendInterested();
                Thread.sleep(500);
                session.sendNotInterested();
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        })) {
            connectPeer(server.getPort());
            assertTrue(server.awaitConnection(2, TimeUnit.SECONDS));
            
            Peer p = waitForPeerState(server.getPort(), 2000);
            assertNotNull(p, "Peer should be connected");
            
            long timeout = System.currentTimeMillis() + 2000;
            while (!p.isInterestedInMe() && System.currentTimeMillis() < timeout) {
                Thread.sleep(50);
            }
            assertTrue(p.isInterestedInMe(), "Peer should be interested");
            
            timeout = System.currentTimeMillis() + 2000;
            while (p.isInterestedInMe() && System.currentTimeMillis() < timeout) {
                Thread.sleep(50);
            }
            assertFalse(p.isInterestedInMe(), "Peer should not be interested");
        }
    }

    @Test
    void testHaveAndBitfield() throws Exception {
        try (MockPeerServer server = new MockPeerServer(localInfoHash, session -> {
            try {
                session.expectAndValidateHandshake();
                session.sendHandshake(localInfoHash, new byte[20]);
                session.sendBitfield(new byte[]{(byte) 0xFF, 0x00});
                session.sendHave(42);
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        })) {
            connectPeer(server.getPort());
            assertTrue(server.awaitConnection(2, TimeUnit.SECONDS));
            
            Peer p = waitForPeerState(server.getPort(), 2000);
            assertNotNull(p, "Peer should be connected");
            // Since HAVE and BITFIELD just log at the moment, checking connection stability is sufficient
        }
    }

    @Test
    void testMalformedMessage() throws Exception {
        try (MockPeerServer server = new MockPeerServer(localInfoHash, session -> {
            try {
                session.expectAndValidateHandshake();
                session.sendHandshake(localInfoHash, new byte[20]);
                Thread.sleep(500); // Give client time to transition to READY and register connection
                session.sendMalformedMessage(); // Will cause ProtocolHandler to throw
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        })) {
            connectPeer(server.getPort());
            assertTrue(server.awaitConnection(2, TimeUnit.SECONDS));
            
            Peer p = waitForPeerState(server.getPort(), 2000);
            assertNotNull(p, "Peer should be connected initially");
            
            // The protocol handler logs and ignores the malformed message (throws IllegalArgumentException caught silently), 
            // but the connection is dropped eventually or kept open depending on implementation.
            // Currently ProtocolException might drop it in handleRead catch block if it wasn't caught inside handleMessage.
            // Wait, we caught it inside handleMessage and rethrew IllegalArgumentException, 
            // which propagates to handleRead catch block -> disconnects.
            
            long timeout = System.currentTimeMillis() + 2000;
            while (peerManager.getConnectedPeers().size() > 0 && System.currentTimeMillis() < timeout) {
                Thread.sleep(50);
            }
            assertEquals(0, peerManager.getConnectedPeers().size(), "Peer should be disconnected due to malformed message");
        }
    }

    @Test
    void testTruncatedMessage() throws Exception {
        try (MockPeerServer server = new MockPeerServer(localInfoHash, session -> {
            try {
                session.expectAndValidateHandshake();
                session.sendHandshake(localInfoHash, new byte[20]);
                session.sendTruncatedMessage();
                Thread.sleep(500); // Server keeps running, leaving client waiting for more bytes
            } catch (Exception e) {
                e.printStackTrace();
            }
        })) {
            connectPeer(server.getPort());
            assertTrue(server.awaitConnection(2, TimeUnit.SECONDS));
            
            Peer p = waitForPeerState(server.getPort(), 2000);
            assertNotNull(p);
            
            // MessageCodec will return null and wait for more data.
            // The connection should remain open.
            Thread.sleep(500);
            assertEquals(1, peerManager.getConnectedPeers().size(), "Connection should remain open waiting for remaining bytes");
        }
    }

    @Test
    void testUnexpectedDisconnect() throws Exception {
        try (MockPeerServer server = new MockPeerServer(localInfoHash, session -> {
            try {
                session.expectAndValidateHandshake();
                session.sendHandshake(localInfoHash, new byte[20]);
                session.disconnect(); // Immediate disconnect
            } catch (Exception e) {
                e.printStackTrace();
            }
        })) {
            connectPeer(server.getPort());
            assertTrue(server.awaitConnection(2, TimeUnit.SECONDS));
            
            // Connection will be dropped shortly after handshake is processed due to EOF
            long timeout = System.currentTimeMillis() + 2000;
            while (peerManager.getConnectedPeers().size() > 0 && System.currentTimeMillis() < timeout) {
                Thread.sleep(50);
            }
            assertEquals(0, peerManager.getConnectedPeers().size(), "Peer should be disconnected");
        }
    }
}
