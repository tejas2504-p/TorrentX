package com.torrentx.download;

import com.torrentx.torrent.TorrentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PieceAssemblerTest {

    private TorrentLayout layout;
    private PieceManager pieceManager;
    private PieceAssembler assembler;
    
    private byte[] validBlock1;
    private byte[] validBlock2;
    private byte[] validHash;

    @BeforeEach
    void setUp() throws Exception {
        // Prepare mock binary data for a piece
        validBlock1 = new byte[16384];
        validBlock2 = new byte[16384];
        
        // Fill with some deterministic data
        for (int i = 0; i < 16384; i++) {
            validBlock1[i] = (byte) (i % 256);
            validBlock2[i] = (byte) ((i + 10) % 256);
        }
        
        byte[] fullPiece = new byte[32768];
        System.arraycopy(validBlock1, 0, fullPiece, 0, 16384);
        System.arraycopy(validBlock2, 0, fullPiece, 16384, 16384);
        
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        validHash = digest.digest(fullPiece);

        // Final piece setup: size 7232
        byte[] finalPieceData = new byte[7232];
        for (int i = 0; i < 7232; i++) {
            finalPieceData[i] = (byte) (i % 256);
        }
        byte[] finalPieceHash = digest.digest(finalPieceData);
        
        List<byte[]> hashes = new ArrayList<>();
        hashes.add(validHash);      // Piece 0
        hashes.add(finalPieceHash); // Piece 1
        
        // Piece length: 32768
        // Total torrent size: 32768 + 7232 = 40000 bytes
        TorrentMetadata metadata = new TorrentMetadata("http://tracker", new byte[20], "test", 32768, hashes, 40000);
        
        layout = new TorrentLayout(metadata, 16384);
        pieceManager = new PieceManager(layout);
        assembler = new PieceAssembler(pieceManager);
    }

    @Test
    void testValidPieceAssemblyAndVerification() {
        pieceManager.markBlockRequested(0, 0, 16384);
        pieceManager.markBlockRequested(0, 16384, 16384);
        
        assertFalse(pieceManager.markBlockReceived(0, 0, validBlock1));
        assertTrue(pieceManager.markBlockReceived(0, 16384, validBlock2)); // Completes piece
        
        assertEquals(PieceState.VERIFYING, pieceManager.getLayout().getPiece(0).getState());
        
        assertTrue(assembler.verifyPiece(0));
        
        assertEquals(PieceState.VERIFIED, pieceManager.getLayout().getPiece(0).getState());
        assertTrue(pieceManager.isPieceComplete(0));
    }

    @Test
    void testInvalidSha1FailsAndResets() {
        pieceManager.markBlockRequested(0, 0, 16384);
        pieceManager.markBlockRequested(0, 16384, 16384);
        
        pieceManager.markBlockReceived(0, 0, validBlock1);
        
        // Tamper with the second block to break SHA-1
        byte[] tamperedBlock2 = validBlock2.clone();
        tamperedBlock2[0] = (byte) ~tamperedBlock2[0];
        
        assertTrue(pieceManager.markBlockReceived(0, 16384, tamperedBlock2));
        
        // Verification should fail
        assertFalse(assembler.verifyPiece(0));
        
        // State should be FAILED, blocks unrequested, memory cleared
        assertEquals(PieceState.FAILED, pieceManager.getLayout().getPiece(0).getState());
        assertFalse(pieceManager.isPieceComplete(0));
        
        // Attempting to get assembled piece now should throw error as buffer is cleared
        assertThrows(IllegalStateException.class, () -> pieceManager.getAssembledPiece(0));
    }

    @Test
    void testPartialPieceVerificationFailsEarly() {
        pieceManager.markBlockRequested(0, 0, 16384);
        pieceManager.markBlockReceived(0, 0, validBlock1);
        
        // Missing second block
        assertFalse(assembler.verifyPiece(0));
    }

    @Test
    void testFinalPieceAndFinalBlock() {
        pieceManager.markBlockRequested(1, 0, 7232); // Final piece, final block
        
        byte[] finalPieceData = new byte[7232];
        for (int i = 0; i < 7232; i++) {
            finalPieceData[i] = (byte) (i % 256);
        }
        
        assertTrue(pieceManager.markBlockReceived(1, 0, finalPieceData));
        
        assertTrue(assembler.verifyPiece(1));
        assertEquals(PieceState.VERIFIED, pieceManager.getLayout().getPiece(1).getState());
    }

    @Test
    void testDuplicateBlockSafelyIgnored() {
        pieceManager.markBlockRequested(0, 0, 16384);
        pieceManager.markBlockReceived(0, 0, validBlock1);
        
        // PieceManager silently overwrites the buffer for a duplicate block if the piece is not yet complete.
        // It should return false because it doesn't trigger the piece completion (the second block is still missing).
        assertFalse(pieceManager.markBlockReceived(0, 0, validBlock1));
    }

    @Test
    void testInvalidBlockOffsetRejected() {
        pieceManager.markBlockRequested(0, 0, 16384);
        
        // Give wrong length
        assertThrows(IllegalArgumentException.class, () -> pieceManager.markBlockReceived(0, 0, new byte[1000]));
        
        // Give wrong offset completely out of bounds
        assertThrows(IllegalArgumentException.class, () -> pieceManager.markBlockReceived(0, 99999, validBlock1));
    }

    @Test
    void testOutOfOrderBlocks() {
        pieceManager.markBlockRequested(0, 0, 16384);
        pieceManager.markBlockRequested(0, 16384, 16384);
        
        // Receive second block FIRST
        assertFalse(pieceManager.markBlockReceived(0, 16384, validBlock2));
        
        // Receive first block LAST
        assertTrue(pieceManager.markBlockReceived(0, 0, validBlock1));
        
        assertTrue(assembler.verifyPiece(0));
    }
}
