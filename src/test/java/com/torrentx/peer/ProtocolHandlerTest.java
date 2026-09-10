package com.torrentx.peer;

import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolHandlerTest {

    private ProtocolHandler protocolHandler;
    private PeerConnection connection;
    private PeerInfo peerInfo;

    @BeforeEach
    void setUp() {
        byte[] localInfoHash = new byte[20];
        byte[] localPeerId = new byte[20];
        protocolHandler = new ProtocolHandler(localInfoHash, localPeerId);
        
        peerInfo = new PeerInfo("127.0.0.1", 6881, new byte[20]);
        connection = new PeerConnection(peerInfo, null);
        connection.transitionState(PeerConnectionState.CONNECTED);
        connection.transitionState(PeerConnectionState.HANDSHAKING);
        connection.transitionState(PeerConnectionState.READY); // Bypass handshake for these tests
    }

    private void feedMessageAndProcess(int messageId, byte[] payload) {
        ByteBuffer readBuffer = connection.getReadBuffer();
        readBuffer.clear();
        
        if (messageId == -1) {
            // Keep alive
            readBuffer.putInt(0);
        } else {
            readBuffer.putInt(1 + payload.length);
            readBuffer.put((byte) messageId);
            readBuffer.put(payload);
        }
        
        // ProtocolHandler expects the buffer to be ready for put, and it flips it internally.
        // Wait, handleRead does:
        // ByteBuffer readBuffer = connection.getReadBuffer();
        // readBuffer.flip();
        // So we don't flip it here, we leave the position at the end of what we wrote.
        
        protocolHandler.handleRead(connection);
    }

    @Test
    void testKeepAlive() {
        // Just verify it doesn't throw and doesn't change state
        feedMessageAndProcess(-1, new byte[0]);
        // State should remain unchanged
        assertTrue(connection.getPeerState().isChokingMe());
        assertFalse(connection.getPeerState().isInterestedInMe());
    }

    @Test
    void testChokeMessage() {
        connection.getPeerState().setChokingMe(false);
        feedMessageAndProcess(0, new byte[0]);
        assertTrue(connection.getPeerState().isChokingMe());
    }

    @Test
    void testUnchokeMessage() {
        assertTrue(connection.getPeerState().isChokingMe()); // Default is true
        feedMessageAndProcess(1, new byte[0]);
        assertFalse(connection.getPeerState().isChokingMe());
    }

    @Test
    void testInterestedMessage() {
        assertFalse(connection.getPeerState().isInterestedInMe()); // Default is false
        feedMessageAndProcess(2, new byte[0]);
        assertTrue(connection.getPeerState().isInterestedInMe());
    }

    @Test
    void testNotInterestedMessage() {
        connection.getPeerState().setInterestedInMe(true);
        feedMessageAndProcess(3, new byte[0]);
        assertFalse(connection.getPeerState().isInterestedInMe());
    }

    @Test
    void testHaveMessage() {
        byte[] payload = new byte[4];
        ByteBuffer.wrap(payload).putInt(42);
        
        // This should log and not throw
        assertDoesNotThrow(() -> feedMessageAndProcess(4, payload));
    }

    @Test
    void testBitfieldMessage() {
        byte[] payload = new byte[]{ (byte) 0xFF, (byte) 0x00 };
        
        // This should log and not throw
        assertDoesNotThrow(() -> feedMessageAndProcess(5, payload));
    }

    @Test
    void testMalformedChokeMessage() {
        // Choke with payload should throw IllegalArgumentException from the catch block
        byte[] invalidPayload = new byte[]{ 0x01 };
        assertThrows(IllegalArgumentException.class, () -> feedMessageAndProcess(0, invalidPayload));
    }
    
    @Test
    void testMalformedHaveMessage() {
        // Have with wrong payload size
        byte[] invalidPayload = new byte[]{ 0x01, 0x02 }; // Only 2 bytes
        assertThrows(IllegalArgumentException.class, () -> feedMessageAndProcess(4, invalidPayload));
    }
}
