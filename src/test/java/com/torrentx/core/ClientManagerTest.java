package com.torrentx.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientManagerTest {

    private ClientManager clientManager;

    @BeforeEach
    void setUp() {
        clientManager = new ClientManager();
    }

    @Test
    void testInitialStateNotRunning() {
        assertFalse(clientManager.isRunning(), "ClientManager should not be running initially.");
    }

    @Test
    void testStartTransition() {
        clientManager.start();
        assertTrue(clientManager.isRunning(), "ClientManager should be running after calling start.");
    }

    @Test
    void testStopTransition() {
        clientManager.start();
        clientManager.stop();
        assertFalse(clientManager.isRunning(), "ClientManager should not be running after calling stop.");
    }

    @Test
    void testDuplicateStartHasNoEffect() {
        clientManager.start();
        assertTrue(clientManager.isRunning());
        clientManager.start();
        assertTrue(clientManager.isRunning(), "ClientManager should remain running on duplicate start.");
    }

    @Test
    void testStopWhenNotRunningHasNoEffect() {
        assertFalse(clientManager.isRunning());
        clientManager.stop();
        assertFalse(clientManager.isRunning(), "ClientManager should remain stopped.");
    }

    @Test
    void testStartWithCustomConfig() {
        com.torrentx.utils.Config customConfig = new com.torrentx.utils.Config();
        // customConfig has defaults since it is newly instantiated
        ClientManager customManager = new ClientManager(customConfig);
        assertFalse(customManager.isRunning());
        customManager.start();
        assertTrue(customManager.isRunning());
        customManager.stop();
        assertFalse(customManager.isRunning());
    }
}
