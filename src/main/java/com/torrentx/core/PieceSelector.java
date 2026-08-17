package com.torrentx.core;

import com.torrentx.peer.PeerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PieceSelector implements PieceSelectionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(PieceSelector.class);
    private final int pieceCount;

    public PieceSelector(int pieceCount) {
        this.pieceCount = pieceCount;
    }

    @Override
    public int selectPiece(PeerConnection peerConnection, BitSet myBitfield, Map<Integer, ActivePiece> activePieces, Collection<PeerConnection> peers) {
        // Bootstrap phase: if we do not have any verified pieces yet, download a random available piece
        if (myBitfield.cardinality() < 1) {
            List<Integer> bootstrapCandidates = new ArrayList<>();
            for (int i = 0; i < pieceCount; i++) {
                if (peerConnection.hasPiece(i) && !myBitfield.get(i) && !activePieces.containsKey(i)) {
                    bootstrapCandidates.add(i);
                }
            }
            if (!bootstrapCandidates.isEmpty()) {
                Random random = new Random();
                int selected = bootstrapCandidates.get(random.nextInt(bootstrapCandidates.size()));
                logger.info("Bootstrap phase: selected random piece index {} (cardinality is 0)", selected);
                return selected;
            }
        }

        // Rarest-first selection
        int[] availability = new int[pieceCount];
        for (PeerConnection conn : peers) {
            BitSet field = conn.getPeerBitfield();
            for (int i = 0; i < pieceCount; i++) {
                if (field.get(i)) {
                    availability[i]++;
                }
            }
        }

        List<Integer> candidateIndices = new ArrayList<>();
        int minAvailability = Integer.MAX_VALUE;

        for (int i = 0; i < pieceCount; i++) {
            if (peerConnection.hasPiece(i) && !myBitfield.get(i) && !activePieces.containsKey(i)) {
                int count = availability[i];
                if (count > 0) {
                    if (count < minAvailability) {
                        minAvailability = count;
                        candidateIndices.clear();
                        candidateIndices.add(i);
                    } else if (count == minAvailability) {
                        candidateIndices.add(i);
                    }
                }
            }
        }

        if (candidateIndices.isEmpty()) {
            return -1;
        }

        Random random = new Random();
        return candidateIndices.get(random.nextInt(candidateIndices.size()));
    }
}
