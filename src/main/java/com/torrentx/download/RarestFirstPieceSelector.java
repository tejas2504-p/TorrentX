package com.torrentx.download;

import com.torrentx.tracker.PeerInfo;

/**
 * Selects pieces based on the Rarest-First algorithm.
 * Thread-safe implementation.
 */
public class RarestFirstPieceSelector implements PieceSelector {

    @Override
    public int selectNextPiece(PeerInfo peer, PieceManager pieceManager, PieceAvailability availability) {
        if (peer == null || pieceManager == null || availability == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }

        int bestPiece = -1;
        int lowestFrequency = Integer.MAX_VALUE;
        
        int totalPieces = pieceManager.getLayout().getTotalPieces();

        for (int i = 0; i < totalPieces; i++) {
            // 1. Must not be already completed
            if (pieceManager.isPieceComplete(i)) {
                continue;
            }

            // 2. Must be available from the selected peer
            if (!availability.peerHasPiece(peer, i)) {
                continue;
            }

            // 3. Must not have been fully assigned already
            Piece piece = pieceManager.getLayout().getPiece(i);
            boolean fullyAssigned = true;
            
            // Synchronize on the Piece object to ensure thread-safe block state checking
            synchronized (piece) {
                if (piece.getState() == PieceState.VERIFYING || piece.getState() == PieceState.VERIFIED) {
                    continue; // Skip verifying pieces
                }
                
                for (Block block : piece.getBlocks()) {
                    if (!block.isRequested()) {
                        fullyAssigned = false;
                        break;
                    }
                }
            }

            if (fullyAssigned) {
                continue;
            }

            // 4. Prefer rare pieces using availability counts
            int frequency = availability.getPieceFrequency(i);
            
            if (frequency < lowestFrequency) {
                lowestFrequency = frequency;
                bestPiece = i;
            } 
            // 5. Break ties deterministically: by iterating ascending, we inherently prefer 
            // the lower piece index in the event of identical frequencies.
        }

        return bestPiece;
    }
}
