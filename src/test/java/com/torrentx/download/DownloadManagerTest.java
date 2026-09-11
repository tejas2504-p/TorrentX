package com.torrentx.download;

import com.torrentx.peer.Peer;
import com.torrentx.peer.PeerConnection;
import com.torrentx.peer.PeerConnectionState;
import com.torrentx.peer.PeerManager;
import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DownloadManagerTest {

    private PeerManager mockPeerManager;
    private PieceManager mockPieceManager;
    private PieceSelector mockPieceSelector;
    private BlockSelector mockBlockSelector;
    private PieceAvailability mockPieceAvailability;
    private PieceAssembler mockPieceAssembler;
    private DownloadManager downloadManager;

    static class TestPeerManager extends PeerManager {
        public java.util.List<PeerConnection> stubPeers = new java.util.ArrayList<>();
        public TestPeerManager() throws Exception { super(new byte[20], new byte[20], 1); }
        @Override public java.util.List<PeerConnection> getConnectedPeers() { return stubPeers; }
        @Override public void setPieceCompletionListener(PieceCompletionListener listener) {}
        @Override public void start() {}
        @Override public void close() {}
    }

    static class TestBlockSelector extends BlockSelector {
        public TestBlockSelector(PieceManager pieceManager) { super(pieceManager, 5); }
        public java.util.List<BlockRequest> stubRequests = java.util.Collections.emptyList();
        @Override public java.util.List<BlockRequest> selectBlocks(com.torrentx.tracker.PeerInfo peer, int pieceIndex) { return stubRequests; }
        @Override public boolean markBlockReceived(com.torrentx.tracker.PeerInfo peer, int pieceIndex, int blockOffset, byte[] blockData) { return true; }
        @Override public int releaseTimedOutRequests(long timeoutMs) { return 0; }
        @Override public void releaseAllPeerRequests(com.torrentx.tracker.PeerInfo peer) {}
    }

    static class TestPieceSelector implements PieceSelector {
        public int stubNextPiece = 0;
        @Override public int selectNextPiece(com.torrentx.tracker.PeerInfo peer, PieceManager pieceManager, PieceAvailability availability) { return stubNextPiece; }
    }

    static class TestPieceAvailability extends PieceAvailability {
        public TestPieceAvailability() { super(10); }
        public boolean stubPeerHasPiece = false;
        @Override public boolean peerHasPiece(com.torrentx.tracker.PeerInfo peer, int pieceIndex) { return stubPeerHasPiece; }
        @Override public void processBitfield(com.torrentx.tracker.PeerInfo peer, byte[] bitfield) {}
        @Override public void processHave(com.torrentx.tracker.PeerInfo peer, int pieceIndex) {}
        @Override public void removePeer(com.torrentx.tracker.PeerInfo peer) {}
        @Override public int getPieceFrequency(int pieceIndex) { return 0; }
    }

    static class TestPieceAssembler extends PieceAssembler {
        public TestPieceAssembler(PieceManager pm) { super(pm); }
        public boolean stubVerifyPiece = true;
        public boolean verifyCalled = false;
        @Override public boolean verifyPiece(int pieceIndex) { verifyCalled = true; return stubVerifyPiece; }
    }

    private TestBlockSelector testBlockSelector;
    private TestPeerManager testPeerManager;
    private TestPieceSelector testPieceSelector;
    private TestPieceAvailability testPieceAvailability;
    private TestPieceAssembler testPieceAssembler;

    @BeforeEach
    void setUp() throws Exception {
        testPeerManager = new TestPeerManager();
        mockPeerManager = testPeerManager;
        
        com.torrentx.torrent.TorrentMetadata metadata = new com.torrentx.torrent.TorrentMetadata("http://localhost:6969/announce", 
            new byte[20], "test.txt", 16384, java.util.Collections.singletonList(new byte[20]), 16384);
        TorrentLayout mockLayout = new TorrentLayout(metadata, 16384); 
        mockPieceManager = new PieceManager(mockLayout);
        
        testPieceSelector = new TestPieceSelector();
        mockPieceSelector = testPieceSelector;
        
        testBlockSelector = new TestBlockSelector(mockPieceManager);
        mockBlockSelector = testBlockSelector;
        
        testPieceAvailability = new TestPieceAvailability();
        mockPieceAvailability = testPieceAvailability;
        
        testPieceAssembler = new TestPieceAssembler(mockPieceManager);
        mockPieceAssembler = testPieceAssembler;
        
        downloadManager = new DownloadManager(mockPeerManager, mockPieceManager,  
                mockPieceSelector, mockBlockSelector, mockPieceAvailability, mockPieceAssembler, null);
    }

    @AfterEach
    void tearDown() {
        downloadManager.close();
    }

    @Test
    void testStartsAndStopsWithoutError() {
        downloadManager.start();
        downloadManager.close();
        // Just checking it doesn't crash on thread initialization/shutdown
    }
    
    static class TestSelectionKey extends SelectionKey {
        public boolean valid = true;
        @Override public boolean isValid() { return valid; }
        @Override public java.nio.channels.SelectableChannel channel() { return null; }
        @Override public Selector selector() { return null; }
        @Override public int interestOps() { return 0; }
        @Override public SelectionKey interestOps(int ops) { return this; }
        @Override public int readyOps() { return 0; }
        @Override public void cancel() {}
    }

    static class TestPeerConnection extends PeerConnection {
        public PeerConnectionState stubState = PeerConnectionState.READY;
        public TestSelectionKey stubKey = new TestSelectionKey();
        public TestPeerConnection(PeerInfo info) { super(info, null); }
        @Override public PeerConnectionState getState() { return stubState; }
        @Override public SelectionKey getSelectionKey() { return stubKey; }
    }

    @Test
    void testCheckInterestedAndRequestBlocks() throws Exception {
        PeerInfo info = new PeerInfo("127.0.0.1", 6881, "peer1".getBytes());
        TestPeerConnection mockConnection = new TestPeerConnection(info);
        
        Peer peer = mockConnection.getPeerState();
        peer.setChokingMe(false); // Unchoked

        testPeerManager.stubPeers.add(mockConnection);
        Queue<ByteBuffer> writeQueue = mockConnection.getWriteQueue();
        
        testPieceAvailability.stubPeerHasPiece = true;
        
        testPieceSelector.stubNextPiece = 0;
        
        BlockRequest br1 = new BlockRequest(0, 0, 16384, info);
        testBlockSelector.stubRequests = Collections.singletonList(br1);

        // Let the scheduler run a bit
        downloadManager.start();
        Thread.sleep(200); 

        // Peer should become interested
        assertTrue(peer.isInterested());
        
        // Write queue should contain INTERESTED message (5 bytes) and REQUEST message (17 bytes)
        boolean hasInterested = false;
        boolean hasRequest = false;
        
        for (ByteBuffer buf : writeQueue) {
            int len = buf.getInt();
            byte id = buf.get();
            if (id == 2) hasInterested = true;
            if (id == 6) hasRequest = true;
        }
        
        assertTrue(hasInterested, "Should have sent INTERESTED");
        assertTrue(hasRequest, "Should have sent REQUEST");
    }

    @Test
    void testOnPieceCompletedVerifiesAsync() throws Exception {
        downloadManager.start();
        
        testPieceAssembler.stubVerifyPiece = true;
        downloadManager.onPieceCompleted(5);
        
        // Give the async pool a moment to run
        Thread.sleep(100);
        
        assertTrue(testPieceAssembler.verifyCalled);
    }
}
