package com.torrentx.download;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Handles the SHA-1 verification of assembled torrent pieces.
 * Verifies piece data securely and transitions PieceManager states.
 */
public class PieceAssembler {

    private final PieceManager pieceManager;

    public PieceAssembler(PieceManager pieceManager) {
        if (pieceManager == null) {
            throw new IllegalArgumentException("PieceManager cannot be null");
        }
        this.pieceManager = pieceManager;
    }

    /**
     * Verifies the SHA-1 hash of a completely assembled piece.
     * Updates the PieceManager state to VERIFIED or FAILED.
     * 
     * @param pieceIndex the index of the piece to verify.
     * @return true if verification succeeds and matches the expected torrent hash.
     */
    public boolean verifyPiece(int pieceIndex) {
        byte[] data;
        try {
            data = pieceManager.getAssembledPiece(pieceIndex);
        } catch (IllegalStateException e) {
            // Piece is not in VERIFYING state, or buffer is missing
            return false;
        }

        byte[] expectedHash = pieceManager.getLayout().getPiece(pieceIndex).getExpectedHash();

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Critical: SHA-1 algorithm not found in this JVM", e);
        }

        byte[] actualHash = digest.digest(data);

        // Raw binary byte-by-byte comparison to prevent hex conversion errors or timing attacks
        boolean isValid = MessageDigest.isEqual(actualHash, expectedHash);

        if (isValid) {
            pieceManager.markPieceVerified(pieceIndex);
        } else {
            pieceManager.markPieceFailed(pieceIndex);
        }
        
        // Prevent mutable data leakage by explicitly zeroing out the defensive clone
        Arrays.fill(data, (byte) 0);

        return isValid;
    }
}
