package com.torrentx.gui.service;

import com.torrentx.core.ClientManager;
import com.torrentx.gui.TorrentRow;
import com.torrentx.torrent.TorrentException;
import com.torrentx.torrent.TorrentMetadata;
import com.torrentx.torrent.TorrentParser;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TorrentService {
    private static final Logger logger = LoggerFactory.getLogger(TorrentService.class);
    
    private final ClientManager clientManager;
    private final ExecutorService backgroundExecutor;
    private final Map<TorrentRow, ActiveTorrent> activeTorrents = new ConcurrentHashMap<>();
    
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
            for (ActiveTorrent active : activeTorrents.values()) {
                active.stop();
            }
            if (clientManager.isRunning()) {
                clientManager.stop();
            }
            backgroundExecutor.shutdownNow();
        } catch (Exception e) {
            logger.error("Error shutting down backend", e);
        }
    }
    
    public void addTorrent(File torrentFile, File saveDir, java.util.function.Consumer<TorrentRow> onAdded) {
        executeTask(() -> {
            try {
                TorrentParser parser = new TorrentParser();
                TorrentMetadata metadata = parser.parse(torrentFile);
                
                Platform.runLater(() -> {
                    TorrentRow row = new TorrentRow(
                        metadata.getName(), "STOPPED", 0.0, 
                        "0 KB/s", "0 KB/s", "0 B", "0 B", 
                        formatBytes(metadata.getTotalLength()), "∞", "0"
                    );
                    
                    ActiveTorrent activeTorrent = new ActiveTorrent(metadata, saveDir.toPath(), row);
                    activeTorrents.put(row, activeTorrent);
                    
                    if (onAdded != null) {
                        onAdded.accept(row);
                    }
                });
            } catch (TorrentException e) {
                logger.error("Failed to parse torrent", e);
            }
        }, "Add Torrent");
    }
    
    public void startTorrent(TorrentRow row) {
        ActiveTorrent active = activeTorrents.get(row);
        if (active != null) {
            executeTask(() -> {
                try {
                    active.start();
                } catch (IOException e) {
                    logger.error("Failed to start torrent: {}", row.nameProperty().get(), e);
                    Platform.runLater(() -> row.statusProperty().set("ERROR"));
                }
            }, "Start Torrent");
        }
    }
    
    public void pauseTorrent(TorrentRow row) {
        ActiveTorrent active = activeTorrents.get(row);
        if (active != null) {
            executeTask(active::pause, "Pause Torrent");
        }
    }
    
    public void stopTorrent(TorrentRow row) {
        ActiveTorrent active = activeTorrents.get(row);
        if (active != null) {
            executeTask(active::stop, "Stop Torrent");
        }
    }
    
    public void removeTorrent(TorrentRow row) {
        ActiveTorrent active = activeTorrents.remove(row);
        if (active != null) {
            executeTask(active::remove, "Remove Torrent");
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
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
