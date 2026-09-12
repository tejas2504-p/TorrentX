package com.torrentx.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TorrentRowTest {

    @Test
    void testTorrentRowInitializationAndProperties() {
        String name = "TestTorrent";
        String status = "Downloading";
        double progress = 0.75;
        String downSpeed = "250 KB/s";
        String upSpeed = "15 KB/s";
        String downloaded = "375 MB";
        String uploaded = "20 MB";
        String size = "500 MB";
        String eta = "10m";
        String peers = "8";

        TorrentRow row = new TorrentRow(name, status, progress, downSpeed, upSpeed, downloaded, uploaded, size, eta, peers);

        assertEquals(name, row.nameProperty().get());
        assertEquals(status, row.statusProperty().get());
        assertEquals(progress, row.progressProperty().get());
        assertEquals(downSpeed, row.downSpeedProperty().get());
        assertEquals(upSpeed, row.upSpeedProperty().get());
        assertEquals(downloaded, row.downloadedProperty().get());
        assertEquals(uploaded, row.uploadedProperty().get());
        assertEquals(size, row.sizeProperty().get());
        assertEquals(eta, row.etaProperty().get());
        assertEquals(peers, row.peersProperty().get());
    }
}
