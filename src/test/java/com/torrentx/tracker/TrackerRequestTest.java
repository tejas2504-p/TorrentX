package com.torrentx.tracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackerRequestTest {

    @Test
    void testValidTrackerRequestCreation() {
        byte[] infoHash = new byte[20];
        infoHash[0] = 1;
        byte[] peerId = new byte[20];
        peerId[0] = 2;

        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .port(6885)
                .uploaded(1024)
                .downloaded(512)
                .left(2048)
                .compact(false)
                .event("started")
                .build();

        assertArrayEquals(infoHash, request.getInfoHash());
        assertArrayEquals(peerId, request.getPeerId());
        assertEquals(6885, request.getPort());
        assertEquals(1024, request.getUploaded());
        assertEquals(512, request.getDownloaded());
        assertEquals(2048, request.getLeft());
        assertFalse(request.isCompact());
        assertEquals("started", request.getEvent());
    }

    @Test
    void testDefaultValues() {
        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];

        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .build();

        assertEquals(6881, request.getPort());
        assertEquals(0, request.getUploaded());
        assertEquals(0, request.getDownloaded());
        assertEquals(0, request.getLeft());
        assertTrue(request.isCompact());
        assertNull(request.getEvent());
    }

    @Test
    void testDefensiveCopying() {
        byte[] infoHash = new byte[20];
        infoHash[0] = 1;
        byte[] peerId = new byte[20];
        peerId[0] = 2;

        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .build();

        // Modify local source arrays
        infoHash[0] = 9;
        peerId[0] = 9;

        // Verify request instance retains original data
        assertEquals(1, request.getInfoHash()[0]);
        assertEquals(2, request.getPeerId()[0]);

        // Modify array returned by getter
        byte[] retrievedHash = request.getInfoHash();
        retrievedHash[0] = 9;

        // Verify request instance is still unaffected
        assertEquals(1, request.getInfoHash()[0]);
    }

    @Test
    void testRejectsNullOrInvalidInfoHash() {
        byte[] peerId = new byte[20];
        TrackerRequest.Builder builder = new TrackerRequest.Builder().peerId(peerId);

        // Null infoHash
        assertThrows(IllegalArgumentException.class, () -> builder.infoHash(null));

        // Short infoHash
        assertThrows(IllegalArgumentException.class, () -> builder.infoHash(new byte[19]));

        // Long infoHash
        assertThrows(IllegalArgumentException.class, () -> builder.infoHash(new byte[21]));

        // Build without setting infoHash
        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    void testRejectsNullOrInvalidPeerId() {
        byte[] infoHash = new byte[20];
        TrackerRequest.Builder builder = new TrackerRequest.Builder().infoHash(infoHash);

        // Null peerId
        assertThrows(IllegalArgumentException.class, () -> builder.peerId(null));

        // Short peerId
        assertThrows(IllegalArgumentException.class, () -> builder.peerId(new byte[19]));

        // Long peerId
        assertThrows(IllegalArgumentException.class, () -> builder.peerId(new byte[21]));

        // Build without setting peerId
        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    void testRejectsInvalidPorts() {
        TrackerRequest.Builder builder = new TrackerRequest.Builder();

        assertThrows(IllegalArgumentException.class, () -> builder.port(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.port(65536));
    }

    @Test
    void testRejectsNegativeCounters() {
        TrackerRequest.Builder builder = new TrackerRequest.Builder();

        assertThrows(IllegalArgumentException.class, () -> builder.uploaded(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.downloaded(-500));
        assertThrows(IllegalArgumentException.class, () -> builder.left(-9999));
    }
}
