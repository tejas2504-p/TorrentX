package com.torrentx;

import com.torrentx.core.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main application entry point for TorrentX.
 * Initializes logging, manages the startup lifecycle, and ensures graceful shutdown on JVM exit.
 */
public class TorrentClient {
    private static final Logger logger = LoggerFactory.getLogger(TorrentClient.class);

    public static void main(String[] args) {
        logger.info("Initializing TorrentX Application...");

        // Create the non-static lifecycle manager instance
        final ClientManager clientManager = new ClientManager();

        // Register a shutdown hook to guarantee clean shutdown on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received. Shutting down gracefully...");
            if (clientManager.isRunning()) {
                clientManager.stop();
            }
        }, "torrentx-shutdown-hook"));

        try {
            // Start the application lifecycle
            clientManager.start();
        } catch (Exception e) {
            logger.error("A critical error occurred during TorrentX startup sequence", e);
            System.exit(1);
        }
    }
}
