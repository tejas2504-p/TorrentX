package com.torrentx.download;

import com.torrentx.torrent.TorrentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PieceManagerTest {

    private TorrentLayout layout;
    private PieceManager manager;

    @BeforeEach
    void setUp() {
        // Create mock metadata: Total 64KB, Piece 32KB, Block 16KB -> 2 Pieces, 4 Blocks total
        List<byte[]> hashes = new ArrayList<>();
        hashes.add(new byte[20]);
        hashes.add(new byte[20]);
        
        TorrentMetadata metadata = new TorrentMetadata("http://tracker", new byte[20], "test", 32768, hashes, 65536);
        layout = new TorrentLayout(metadata, 16384);
        manager = new PieceManager(layout);
    }

    @Test
    void testInitialState() {
        assertFalse(manager.isPieceComplete(0));
        assertFalse(manager.isPieceComplete(1));
    }

    @Test
    void testSuccessfulPieceAssemblyAndVerification() {
        // Block 0
        manager.markBlockRequested(0, 0, 16384);
        byte[] b0 = new byte[16384];
        Arrays.fill(b0, (byte) 1);
        boolean complete0 = manager.markBlockReceived(0, 0, b0);
        assertFalse(complete0);
        assertFalse(manager.isPieceComplete(0));
        assertEquals(PieceState.DOWNLOADING, layout.getPiece(0).getState());

        // Block 1
        manager.markBlockRequested(0, 16384, 16384);
        byte[] b1 = new byte[16384];
        Arrays.fill(b1, (byte) 2);
        boolean complete1 = manager.markBlockReceived(0, 16384, b1);
        
        assertTrue(complete1);
        assertEquals(PieceState.VERIFYING, layout.getPiece(0).getState());
        assertFalse(manager.isPieceComplete(0)); // NOT complete until verified!
        
        // Assemble and check data
        byte[] assembled = manager.getAssembledPiece(0);
        assertEquals(32768, assembled.length);
        assertEquals((byte) 1, assembled[0]);
        assertEquals((byte) 1, assembled[16383]);
        assertEquals((byte) 2, assembled[16384]);
        assertEquals((byte) 2, assembled[32767]);

        // Verify
        manager.markPieceVerified(0);
        assertEquals(PieceState.VERIFIED, layout.getPiece(0).getState());
        assertTrue(manager.isPieceComplete(0));
    }

    @Test
    void testFailedVerificationReset() {
        manager.markBlockRequested(1, 0, 16384);
        manager.markBlockReceived(1, 0, new byte[16384]);
        manager.markBlockRequested(1, 16384, 16384);
        assertTrue(manager.markBlockReceived(1, 16384, new byte[16384]));
        
        assertEquals(PieceState.VERIFYING, layout.getPiece(1).getState());
        manager.markPieceFailed(1);
        
        assertEquals(PieceState.FAILED, layout.getPiece(1).getState());
        assertFalse(manager.isPieceComplete(1));
        
        // Ensure blocks are reset and need to be requested again
        assertFalse(layout.getPiece(1).getBlocks().get(0).isRequested());
    }

    @Test
    void testPreventReceivingUnrequestedBlock() {
        assertThrows(IllegalStateException.class, () -> manager.markBlockReceived(0, 0, new byte[16384]));
    }

    @Test
    void testPreventDuplicateCompletion() {
        manager.markBlockRequested(0, 0, 16384);
        manager.markBlockReceived(0, 0, new byte[16384]);
        manager.markBlockRequested(0, 16384, 16384);
        manager.markBlockReceived(0, 16384, new byte[16384]);
        manager.markPieceVerified(0);
        
        // Trying to request again should fail
        assertThrows(IllegalStateException.class, () -> manager.markBlockRequested(0, 0, 16384));
        
        // Trying to receive a delayed duplicate block should return false safely
        assertFalse(manager.markBlockReceived(0, 0, new byte[16384]));
    }

    @Test
    void testInvalidOffsetsAndIndexes() {
        assertThrows(IndexOutOfBoundsException.class, () -> manager.markBlockRequested(2, 0, 16384));
        assertThrows(IllegalArgumentException.class, () -> manager.markBlockRequested(0, 9999, 16384));
    }
    
    @Test
    void testInvalidDataLength() {
        manager.markBlockRequested(0, 0, 16384);
        assertThrows(IllegalArgumentException.class, () -> manager.markBlockReceived(0, 0, new byte[1000]));
    }
}
