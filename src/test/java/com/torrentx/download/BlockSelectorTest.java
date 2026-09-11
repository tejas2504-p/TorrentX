package com.torrentx.download;

import com.torrentx.torrent.TorrentMetadata;
import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockSelectorTest {

    private TorrentLayout layout;
    private PieceManager manager;
    private BlockSelector selector;
    private PeerInfo peer1;
    private PeerInfo peer2;

    @BeforeEach
    void setUp() {
        List<byte[]> hashes = new ArrayList<>();
        hashes.add(new byte[20]); // Piece 0
        hashes.add(new byte[20]); // Piece 1
        // Total torrent size: 40000. Piece length: 32768.
        // Piece 0: 32768 bytes
        // Piece 1: 7232 bytes (final piece)
        TorrentMetadata metadata = new TorrentMetadata("http://tracker", new byte[20], "test", 32768, hashes, 40000);
        
        layout = new TorrentLayout(metadata, 16384);
        manager = new PieceManager(layout);
        
        // Max 2 in-flight requests per peer
        selector = new BlockSelector(manager, 2);
        
        peer1 = new PeerInfo("127.0.0.1", 6881, new byte[20]);
        peer2 = new PeerInfo("127.0.0.2", 6881, new byte[20]);
    }

    @Test
    void testNormalBlockSelection() {
        List<BlockRequest> reqs = selector.selectBlocks(peer1, 0);
        
        // Since max is 2, and piece 0 has 2 blocks, it should select both
        assertEquals(2, reqs.size());
        assertEquals(0, reqs.get(0).getOffset());
        assertEquals(16384, reqs.get(0).getLength());
        assertEquals(16384, reqs.get(1).getOffset());
        assertEquals(16384, reqs.get(1).getLength());
        
        assertEquals(2, selector.getInFlightCountForPeer(peer1));
        
        // Cannot select more due to maxInFlightRequests
        assertTrue(selector.selectBlocks(peer1, 1).isEmpty());
    }

    @Test
    void testFinalBlockHandling() {
        // Piece 1 is 7232 bytes long, so it fits inside 1 block of 7232 bytes
        List<BlockRequest> reqs = selector.selectBlocks(peer1, 1);
        assertEquals(1, reqs.size());
        assertEquals(0, reqs.get(0).getOffset());
        assertEquals(7232, reqs.get(0).getLength());
    }

    @Test
    void testDuplicatePrevention() {
        selector.selectBlocks(peer1, 0); // Takes both blocks of Piece 0
        
        // Peer 2 asks for piece 0, but all blocks are already taken
        List<BlockRequest> peer2Reqs = selector.selectBlocks(peer2, 0);
        assertTrue(peer2Reqs.isEmpty());
        
        assertEquals(0, selector.getInFlightCountForPeer(peer2));
    }

    @Test
    void testRequestCompletionAndRelease() {
        List<BlockRequest> reqs = selector.selectBlocks(peer1, 0);
        assertEquals(2, selector.getInFlightCountForPeer(peer1));
        
        // Fulfill first block
        selector.markBlockReceived(peer1, 0, 0, new byte[16384]);
        
        // In-flight count drops
        assertEquals(1, selector.getInFlightCountForPeer(peer1));
        
        // Peer 1 can now request another block (Piece 1, block 0)
        List<BlockRequest> newReqs = selector.selectBlocks(peer1, 1);
        assertEquals(1, newReqs.size());
        assertEquals(2, selector.getInFlightCountForPeer(peer1));
    }

    @Test
    void testRequestFailureAndReassignment() {
        selector.selectBlocks(peer1, 0);
        assertEquals(2, selector.getInFlightCountForPeer(peer1));
        
        // Peer 1 drops connection, release its blocks
        selector.releaseAllPeerRequests(peer1);
        assertEquals(0, selector.getInFlightCountForPeer(peer1));
        
        // Peer 2 should now be able to request those exact blocks
        List<BlockRequest> peer2Reqs = selector.selectBlocks(peer2, 0);
        assertEquals(2, peer2Reqs.size());
    }

    @Test
    void testTimeoutRelease() throws InterruptedException {
        selector.selectBlocks(peer1, 1);
        
        // Need to simulate a timeout. Wait 50ms, timeout threshold 10ms
        Thread.sleep(50);
        
        int released = selector.releaseTimedOutRequests(10);
        assertEquals(1, released);
        
        assertEquals(0, selector.getInFlightCountForPeer(peer1));
    }

    @Test
    void testConstructorValidations() {
        assertThrows(IllegalArgumentException.class, () -> new BlockSelector(null, 5));
        assertThrows(IllegalArgumentException.class, () -> new BlockSelector(manager, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockRequest(0, -1, 16384, peer1));
    }
}
