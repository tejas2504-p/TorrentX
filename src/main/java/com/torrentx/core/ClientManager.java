package com.torrentx.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle of the TorrentX client application.
 * Responsible for core service initialization, state monitoring, and clean shutdown procedures.
 */
public class ClientManager {
    private static final Logger logger = LoggerFactory.getLogger(ClientManager.class);
    
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Initializes a new ClientManager.
     */
    public ClientManager() {
        logger.debug("ClientManager instantiated.");
    }

    /**
     * Starts the core TorrentX application services.
     * Synchronized to prevent concurrent startup sequences.
     */
    public synchronized void start() {
        if (running.get()) {
            logger.warn("TorrentX Client is already running.");
            return;
        }

        logger.info("Starting TorrentX Client core services...");
        
        // TODO: In future phases, initialize core components here (e.g., storage, engine, network)
        
        running.set(true);
        logger.info("TorrentX Client started successfully.");
    }

    /**
     * Stops the running TorrentX application and cleans up resources.
     * Synchronized to ensure clean shutdown logic runs to completion.
     */
    public synchronized void stop() {
        if (!running.get()) {
            logger.warn("TorrentX Client is not running.");
            return;
        }

        logger.info("Shutting down TorrentX Client core services...");
        
        // TODO: In future phases, stop active downloads, close network connections, and flush files
        
        running.set(false);
        logger.info("TorrentX Client core services shutdown complete.");
    }

    /**
     * Checks if the client manager is currently running.
     *
     * @return true if running, false otherwise.
     */
    public boolean isRunning() {
        return running.get();
    }
}
