package com.torrentx.peer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeerStateManagerTest {

    private PeerStateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new PeerStateManager();
    }

    @Test
    void testInitialState() {
        assertEquals(PeerConnectionState.CONNECTING, stateManager.getState());
    }

    @Test
    void testValidTransitionsSuccessPath() {
        assertTrue(stateManager.transition(PeerConnectionState.CONNECTED));
        assertEquals(PeerConnectionState.CONNECTED, stateManager.getState());

        assertTrue(stateManager.transition(PeerConnectionState.HANDSHAKING));
        assertEquals(PeerConnectionState.HANDSHAKING, stateManager.getState());

        assertTrue(stateManager.transition(PeerConnectionState.READY));
        assertEquals(PeerConnectionState.READY, stateManager.getState());

        assertTrue(stateManager.transition(PeerConnectionState.CLOSING));
        assertEquals(PeerConnectionState.CLOSING, stateManager.getState());

        assertTrue(stateManager.transition(PeerConnectionState.DISCONNECTED));
        assertEquals(PeerConnectionState.DISCONNECTED, stateManager.getState());
    }

    @Test
    void testValidTransitionsFailurePath() {
        // Can fail from CONNECTING
        assertTrue(stateManager.transition(PeerConnectionState.FAILED));
        assertEquals(PeerConnectionState.FAILED, stateManager.getState());
        
        // FAILED cleans up to DISCONNECTED
        assertTrue(stateManager.transition(PeerConnectionState.DISCONNECTED));
        assertEquals(PeerConnectionState.DISCONNECTED, stateManager.getState());
    }

    @Test
    void testInvalidTransitionThrowsException() {
        // CONNECTING directly to READY is invalid
        assertThrows(IllegalStateException.class, () -> {
            stateManager.transition(PeerConnectionState.READY);
        });
        
        // State should remain CONNECTING
        assertEquals(PeerConnectionState.CONNECTING, stateManager.getState());
    }
    
    @Test
    void testTransitionToSameState() {
        assertTrue(stateManager.transition(PeerConnectionState.CONNECTING));
        assertEquals(PeerConnectionState.CONNECTING, stateManager.getState());
    }
}
