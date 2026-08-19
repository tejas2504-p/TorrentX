package com.torrentx.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TorrentRowTest {

    @Test
    void testTorrentRowInitializationAndProperties() {
        String name = "TestTorrent";
        String size = "500 MB";
        double progress = 0.75;
        String status = "Downloading";
        String downSpeed = "250 KB/s";
        String upSpeed = "15 KB/s";
        String peers = "8";

        TorrentRow row = new TorrentRow(name, size, progress, status, downSpeed, upSpeed, peers);

        assertEquals(name, row.nameProperty().get());
        assertEquals(size, row.sizeProperty().get());
        assertEquals(progress, row.progressProperty().get());
        assertEquals(status, row.statusProperty().get());
        assertEquals(downSpeed, row.downSpeedProperty().get());
        assertEquals(upSpeed, row.upSpeedProperty().get());
        assertEquals(peers, row.peersProperty().get());
    }
}
