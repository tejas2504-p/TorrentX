package com.torrentx.core;

import com.torrentx.peer.PeerConnection;

import java.util.*;

public class PieceSelector {
    private final int pieceCount;

    public PieceSelector(int pieceCount) {
        this.pieceCount = pieceCount;
    }

    public int selectRarestPiece(PeerConnection peerConnection, BitSet myBitfield, Map<Integer, ActivePiece> activePieces, Collection<PeerConnection> peers) {
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
