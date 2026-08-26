package com.torrentx.tracker;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class TrackerSessionTest {

    private final byte[] infoHash = "12345678901234567890".getBytes(StandardCharsets.UTF_8);
    private final byte[] peerId = "-TX1000-abcdefghijkl".getBytes(StandardCharsets.UTF_8);
    private final int port = 6881;

    @Test
    void testSessionInitialization() {
        TrackerSession session = new TrackerSession(infoHash, peerId, port, 100000, 40000);

        assertArrayEquals(infoHash, session.getInfoHash());
        assertArrayEquals(peerId, session.getPeerId());
        assertEquals(port, session.getPort());
        assertEquals(100000, session.getTotalLength());
        assertEquals(40000, session.getDownloaded());
        assertEquals(60000, session.getLeft());
        assertEquals(0, session.getUploaded());

        assertFalse(session.isStartedSent());
        assertFalse(session.isCompletedSent());
        assertFalse(session.isStoppedSent());
    }

    @Test
    void testInvalidConstructorParameters() {
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(null, peerId, port, 1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(new byte[19], peerId, port, 1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(infoHash, null, port, 1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(infoHash, new byte[21], port, 1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(infoHash, peerId, -1, 1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(infoHash, peerId, 65536, 1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(infoHash, peerId, port, -1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(infoHash, peerId, port, 1000, -10));
        assertThrows(IllegalArgumentException.class, () -> new TrackerSession(infoHash, peerId, port, 1000, 1001));
    }

    @Test
    void testNormalLifecycleStateTransitions() {
        // Starts incomplete
        TrackerSession session = new TrackerSession(infoHash, peerId, port, 10000, 0);

        // 1. Started event
        TrackerRequest startReq = session.createStartedRequest();
        assertNotNull(startReq);
        assertEquals("started", startReq.getEvent());
        assertEquals(10000, startReq.getLeft());
        assertEquals(0, startReq.getDownloaded());
        assertTrue(session.isStartedSent());

        // Attempting to send started again should throw
        assertThrows(IllegalStateException.class, session::createStartedRequest);

        // 2. Periodic update during download
        session.updateStats(500, 4000);
        TrackerRequest updateReq1 = session.createUpdateRequest();
        assertNotNull(updateReq1);
        assertNull(updateReq1.getEvent()); // Omitted event for normal updates
        assertEquals(6000, updateReq1.getLeft());
        assertEquals(4000, updateReq1.getDownloaded());
        assertEquals(500, updateReq1.getUploaded());

        // 3. Complete event transition
        session.updateStats(1000, 10000); // 100% complete
        TrackerRequest completeReq = session.createUpdateRequest();
        assertNotNull(completeReq);
        assertEquals("completed", completeReq.getEvent());
        assertEquals(0, completeReq.getLeft());
        assertEquals(10000, completeReq.getDownloaded());
        assertTrue(session.isCompletedSent());

        // 4. Subsequent updates after completion
        session.updateStats(2000, 10000);
        TrackerRequest updateReq2 = session.createUpdateRequest();
        assertNotNull(updateReq2);
        assertNull(updateReq2.getEvent()); // Completed should not be repeated
        assertEquals(2000, updateReq2.getUploaded());

        // 5. Stopped event
        TrackerRequest stopReq = session.createStoppedRequest();
        assertNotNull(stopReq);
        assertEquals("stopped", stopReq.getEvent());
        assertTrue(session.isStoppedSent());
        assertFalse(session.isStartedSent()); // started reset

        // Subsequent stops should return null
        assertNull(session.createStoppedRequest());
    }

    @Test
    void testStartedAlreadyCompleted() {
        // Already 100% complete at startup
        TrackerSession session = new TrackerSession(infoHash, peerId, port, 10000, 10000);
        assertTrue(session.isCompletedSent()); // Automatically marked complete

        TrackerRequest startReq = session.createStartedRequest();
        assertEquals("started", startReq.getEvent());
        assertEquals(0, startReq.getLeft());

        // Next update should NOT send completed
        TrackerRequest updateReq = session.createUpdateRequest();
        assertNull(updateReq.getEvent());
    }

    @Test
    void testIllegalStateAccess() {
        TrackerSession session = new TrackerSession(infoHash, peerId, port, 1000, 0);

        // Cannot update before started
        assertThrows(IllegalStateException.class, session::createUpdateRequest);

        // Stopping before started returns null
        assertNull(session.createStoppedRequest());
    }

    @Test
    void testStatsConstraints() {
        TrackerSession session = new TrackerSession(infoHash, peerId, port, 10000, 2000);

        // Uploaded cannot decrease
        session.updateStats(100, 3000);
        assertThrows(IllegalArgumentException.class, () -> session.updateStats(99, 3000));

        // Downloaded cannot decrease
        assertThrows(IllegalArgumentException.class, () -> session.updateStats(100, 2999));

        // Downloaded cannot exceed total length
        assertThrows(IllegalArgumentException.class, () -> session.updateStats(100, 10001));
    }
}
