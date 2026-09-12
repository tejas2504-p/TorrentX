package com.torrentx.gui.service;

import com.torrentx.core.ClientManager;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TorrentService {
    private static final Logger logger = LoggerFactory.getLogger(TorrentService.class);
    
    private final ClientManager clientManager;
    private final ExecutorService backgroundExecutor;
    
    public TorrentService(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.backgroundExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TorrentService-BgThread");
            t.setDaemon(true);
            return t;
        });
    }

    public void startBackend() {
        backgroundExecutor.submit(() -> {
            try {
                if (!clientManager.isRunning()) {
                    clientManager.start();
                }
            } catch (Exception e) {
                logger.error("Failed to start backend", e);
            }
        });
    }

    public void stopBackend() {
        try {
            if (clientManager.isRunning()) {
                clientManager.stop();
            }
            backgroundExecutor.shutdownNow();
        } catch (Exception e) {
            logger.error("Error shutting down backend", e);
        }
    }

    public void executeTask(Runnable runnable, String taskName) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                logger.debug("Background task started: {}", taskName);
                runnable.run();
                return null;
            }
        };
        
        task.setOnFailed(e -> logger.error("Background task failed: {}", taskName, task.getException()));
        backgroundExecutor.submit(task);
    }
}
