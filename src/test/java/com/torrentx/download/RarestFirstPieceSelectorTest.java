package com.torrentx.download;

import com.torrentx.torrent.TorrentMetadata;
import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RarestFirstPieceSelectorTest {

    private TorrentLayout layout;
    private PieceManager pieceManager;
    private PieceAvailability availability;
    private RarestFirstPieceSelector selector;
    private PeerInfo peer1;
    private PeerInfo peer2;
    private PeerInfo peer3;

    @BeforeEach
    void setUp() {
        // Create 5 pieces for testing
        List<byte[]> hashes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            hashes.add(new byte[20]);
        }
        
        TorrentMetadata metadata = new TorrentMetadata("http://tracker", new byte[20], "test", 32768, hashes, 5 * 32768);
        layout = new TorrentLayout(metadata, 16384); // Each piece has 2 blocks
        pieceManager = new PieceManager(layout);
        availability = new PieceAvailability(5);
        selector = new RarestFirstPieceSelector();
        
        peer1 = new PeerInfo("127.0.0.1", 6881, new byte[20]);
        peer2 = new PeerInfo("127.0.0.2", 6881, new byte[20]);
        peer3 = new PeerInfo("127.0.0.3", 6881, new byte[20]);
    }

    @Test
    void testSinglePeerRarestFirst() {
        // Peer 1 has pieces 1, 2, 4
        availability.processHave(peer1, 1);
        availability.processHave(peer1, 2);
        availability.processHave(peer1, 4);
        
        // Peer 2 has piece 1 (so piece 1 frequency = 2)
        availability.processHave(peer2, 1);
        
        // When peer1 is asked, pieces available: 1, 2, 4. 
        // Piece 1 freq: 2. Piece 2 freq: 1. Piece 4 freq: 1.
        // Piece 2 and 4 are tied for rarest. Tie breaker prefers lower index.
        assertEquals(2, selector.selectNextPiece(peer1, pieceManager, availability));
    }

    @Test
    void testUnavailablePieces() {
        // Peer 1 has piece 0
        availability.processHave(peer1, 0);
        // Peer 2 has piece 1
        availability.processHave(peer2, 1);
        
        // Asking for peer2, it should only return piece 1, even though piece 0 is tied for frequency.
        assertEquals(1, selector.selectNextPiece(peer2, pieceManager, availability));
    }

    @Test
    void testCompletedPiecesAreIgnored() {
        availability.processHave(peer1, 0);
        availability.processHave(peer1, 1);
        
        // Fake verification to mark piece 0 complete
        pieceManager.markBlockRequested(0, 0, 16384);
        pieceManager.markBlockReceived(0, 0, new byte[16384]);
        pieceManager.markBlockRequested(0, 16384, 16384);
        pieceManager.markBlockReceived(0, 16384, new byte[16384]);
        pieceManager.markPieceVerified(0); // Completed
        
        // Piece 0 freq: 1, Piece 1 freq: 1. Piece 0 is complete. Must pick 1.
        assertEquals(1, selector.selectNextPiece(peer1, pieceManager, availability));
    }

    @Test
    void testFullyAssignedPiecesAreIgnored() {
        availability.processHave(peer1, 0);
        availability.processHave(peer1, 1);
        
        // Request all blocks of piece 0 (so it is fully assigned but not complete)
        pieceManager.markBlockRequested(0, 0, 16384);
        pieceManager.markBlockRequested(0, 16384, 16384);
        
        // Must pick piece 1
        assertEquals(1, selector.selectNextPiece(peer1, pieceManager, availability));
    }

    @Test
    void testEmptyAvailability() {
        // No peers have pieces
        assertEquals(-1, selector.selectNextPiece(peer1, pieceManager, availability));
    }

    @Test
    void testTieBreaking() {
        availability.processHave(peer1, 2);
        availability.processHave(peer1, 3);
        availability.processHave(peer1, 4);
        
        // Frequencies are all 1. Should pick the lowest index.
        assertEquals(2, selector.selectNextPiece(peer1, pieceManager, availability));
    }

    @Test
    void testMultiplePeersRarestFirst() {
        // Setup varying rarities
        availability.processHave(peer1, 0);
        availability.processHave(peer2, 0);
        availability.processHave(peer3, 0); // freq 3
        
        availability.processHave(peer1, 1);
        availability.processHave(peer2, 1); // freq 2
        
        availability.processHave(peer1, 2); // freq 1
        
        availability.processHave(peer1, 3);
        availability.processHave(peer2, 3);
        availability.processHave(peer3, 3); // freq 3
        
        availability.processHave(peer1, 4); // freq 1
        
        // For peer1, pieces available: 0, 1, 2, 3, 4.
        // Frequencies: 0(3), 1(2), 2(1), 3(3), 4(1).
        // Rarest are 2 and 4. Tie breaker picks 2.
        assertEquals(2, selector.selectNextPiece(peer1, pieceManager, availability));
        
        // fully assign 2
        pieceManager.markBlockRequested(2, 0, 16384);
        pieceManager.markBlockRequested(2, 16384, 16384);
        
        // Next rarest is 4
        assertEquals(4, selector.selectNextPiece(peer1, pieceManager, availability));
        
        // fully assign 4
        pieceManager.markBlockRequested(4, 0, 16384);
        pieceManager.markBlockRequested(4, 16384, 16384);
        
        // Next rarest is 1
        assertEquals(1, selector.selectNextPiece(peer1, pieceManager, availability));
    }
}
