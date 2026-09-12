package com.torrentx.gui.service;

import com.torrentx.core.ClientManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TorrentServiceTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    private StubClientManager clientManager;
    private TorrentService torrentService;

    class StubClientManager extends ClientManager {
        public AtomicBoolean startCalled = new AtomicBoolean(false);
        public AtomicBoolean stopCalled = new AtomicBoolean(false);
        private boolean running = false;

        @Override
        public void start() {
            startCalled.set(true);
            running = true;
        }

        @Override
        public void stop() {
            stopCalled.set(true);
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
        
        public void setRunning(boolean running) {
            this.running = running;
        }
    }

    @BeforeEach
    void setUp() {
        clientManager = new StubClientManager();
        torrentService = new TorrentService(clientManager);
    }

    @AfterEach
    void tearDown() {
        torrentService.stopBackend();
    }

    @Test
    void testStartBackend() throws InterruptedException {
        clientManager.setRunning(false);
        
        torrentService.startBackend();
        
        // Wait briefly for the background thread to execute
        Thread.sleep(100);
        
        assertTrue(clientManager.startCalled.get(), "start() should be called");
    }

    @Test
    void testStopBackend() {
        clientManager.setRunning(true);
        
        torrentService.stopBackend();
        
        assertTrue(clientManager.stopCalled.get(), "stop() should be called");
    }

    @Test
    void testExecuteTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        torrentService.executeTask(latch::countDown, "TestTask");
        
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Background task should complete");
    }
}
