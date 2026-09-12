package com.torrentx.gui.service;

import com.torrentx.download.*;
import com.torrentx.gui.TorrentRow;
import com.torrentx.peer.PeerManager;
import com.torrentx.torrent.TorrentMetadata;
import com.torrentx.upload.UploadManager;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Encapsulates a live torrent session, bridging the UI state with the backend engine.
 */
public class ActiveTorrent {
    private static final Logger LOGGER = Logger.getLogger(ActiveTorrent.class.getName());

    private final TorrentMetadata metadata;
    private final TorrentRow torrentRow;
    private final Path saveDir;

    private PeerManager peerManager;
    private PieceManager pieceManager;
    private PieceAvailability pieceAvailability;
    private PieceAssembler pieceAssembler;
    private BlockSelector blockSelector;
    private DiskWriter diskWriter;
    private DownloadManager downloadManager;
    private UploadManager uploadManager;

    private ScheduledExecutorService poller;
    private volatile boolean running = false;
    private volatile String status = "STOPPED";

    public ActiveTorrent(TorrentMetadata metadata, Path saveDir, TorrentRow torrentRow) {
        this.metadata = metadata;
        this.saveDir = saveDir;
        this.torrentRow = torrentRow;
        updateUIStatus("STOPPED");
    }

    public TorrentRow getTorrentRow() {
        return torrentRow;
    }

    public TorrentMetadata getMetadata() {
        return metadata;
    }

    public synchronized void start() throws IOException {
        if (running) return;

        LOGGER.info("Starting ActiveTorrent: " + metadata.getName());
        updateUIStatus("CONNECTING");

        TorrentLayout layout = new TorrentLayout(metadata, 16384);
        pieceManager = new PieceManager(layout);
        pieceAvailability = new PieceAvailability(metadata.getPieceCount());
        pieceAssembler = new PieceAssembler(pieceManager);
        blockSelector = new BlockSelector(pieceManager, 5); // 5 in-flight max per peer

        // 20 bytes random peer ID for testing
        byte[] peerId = "-TX1000-0123456789AB".getBytes();
        peerManager = new PeerManager(metadata.getInfoHash(), peerId, 50);
        peerManager.setPieceAvailability(pieceAvailability);
        peerManager.setBlockSelector(blockSelector);

        diskWriter = new DiskWriter(metadata, saveDir);

        uploadManager = new UploadManager(4);
        peerManager.setUploadManager(uploadManager);

        downloadManager = new DownloadManager(peerManager, pieceManager,
                new RarestFirstPieceSelector(), blockSelector, pieceAvailability, pieceAssembler, diskWriter);

        // Bind peer manager
        peerManager.bind(0); // auto-assign port
        peerManager.start();
        uploadManager.start();
        downloadManager.start();

        running = true;
        updateUIStatus("DOWNLOADING");

        startPoller();
    }

    public synchronized void pause() {
        if (!running) return;
        LOGGER.info("Pausing ActiveTorrent: " + metadata.getName());
        stopEngine();
        updateUIStatus("PAUSED");
    }

    public synchronized void stop() {
        if (!running) return;
        LOGGER.info("Stopping ActiveTorrent: " + metadata.getName());
        stopEngine();
        updateUIStatus("STOPPED");
    }
    
    public synchronized void remove() {
        stopEngine();
        // Here we could also delete files if needed in future
    }

    private void stopEngine() {
        running = false;
        if (poller != null) {
            poller.shutdownNow();
        }
        if (downloadManager != null) downloadManager.close();
        if (uploadManager != null) uploadManager.close();
        if (peerManager != null) peerManager.close();
        if (diskWriter != null) {
            try {
                diskWriter.close();
            } catch (Exception e) {
                LOGGER.warning("Error closing disk writer: " + e.getMessage());
            }
        }
    }

    private void startPoller() {
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            if (!running) return;

            long downloadedBytes = downloadManager.getBytesDownloaded();
            double progress = downloadManager.getDownloadPercentage();
            int activePeers = downloadManager.getActivePeers();
            boolean complete = downloadManager.isComplete();
            
            Platform.runLater(() -> {
                torrentRow.progressProperty().set(progress);
                torrentRow.downloadedProperty().set(formatBytes(downloadedBytes));
                torrentRow.peersProperty().set(String.valueOf(activePeers));
                if (complete) {
                    updateUIStatus("COMPLETED");
                }
            });
            
            if (complete) {
                if (status.equals("DOWNLOADING")) {
                    updateUIStatus("SEEDING");
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    private void updateUIStatus(String newStatus) {
        this.status = newStatus;
        Platform.runLater(() -> torrentRow.statusProperty().set(newStatus));
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
