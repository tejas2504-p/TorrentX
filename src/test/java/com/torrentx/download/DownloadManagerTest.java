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

    private TestBlockSelector testBlockSelector;
    private TestPeerManager testPeerManager;

    @BeforeEach
    void setUp() throws Exception {
        testPeerManager = new TestPeerManager();
        mockPeerManager = testPeerManager;
        
        com.torrentx.torrent.TorrentMetadata metadata = new com.torrentx.torrent.TorrentMetadata("http://localhost:6969/announce", 
            new byte[20], "test.txt", 16384, java.util.Collections.singletonList(new byte[20]), 16384);
        TorrentLayout mockLayout = new TorrentLayout(metadata, 16384); 
        mockPieceManager = new PieceManager(mockLayout);
        
        mockPieceSelector = mock(PieceSelector.class);
        testBlockSelector = new TestBlockSelector(mockPieceManager);
        mockBlockSelector = testBlockSelector;
        mockPieceAvailability = mock(PieceAvailability.class);
        mockPieceAssembler = mock(PieceAssembler.class);
        
        downloadManager = new DownloadManager(mockPeerManager, mockPieceManager,  
                mockPieceSelector, mockBlockSelector, mockPieceAvailability, mockPieceAssembler);
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
    
    @Test
    void testCheckInterestedAndRequestBlocks() throws Exception {
        PeerInfo info = new PeerInfo("127.0.0.1", 6881, "peer1".getBytes());
        PeerConnection mockConnection = mock(PeerConnection.class);
        when(mockConnection.getPeerInfo()).thenReturn(info);
        when(mockConnection.getState()).thenReturn(PeerConnectionState.READY);
        
        SelectionKey mockKey = mock(SelectionKey.class);
        when(mockConnection.getSelectionKey()).thenReturn(mockKey);
        when(mockKey.isValid()).thenReturn(true);
        
        Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
        when(mockConnection.getWriteQueue()).thenReturn(writeQueue);
        
        Peer peer = new Peer(info);
        peer.setChokingMe(false); // Unchoked
        when(mockConnection.getPeerState()).thenReturn(peer);

        testPeerManager.stubPeers.add(mockConnection);
        
        when(mockPieceAvailability.peerHasPiece(info, 0)).thenReturn(true);
        
        when(mockPieceSelector.selectNextPiece(info, mockPieceManager, mockPieceAvailability)).thenReturn(0);
        
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
            buf.flip();
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
        
        when(mockPieceAssembler.verifyPiece(5)).thenReturn(true);
        downloadManager.onPieceCompleted(5);
        
        // Give the async pool a moment to run
        Thread.sleep(100);
        
        verify(mockPieceAssembler, times(1)).verifyPiece(5);
    }
}
