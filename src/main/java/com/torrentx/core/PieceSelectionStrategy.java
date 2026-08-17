package com.torrentx.core;

import com.torrentx.peer.PeerConnection;
import java.util.BitSet;
import java.util.Collection;
import java.util.Map;

public interface PieceSelectionStrategy {
    int selectPiece(PeerConnection peerConnection, BitSet myBitfield, Map<Integer, ActivePiece> activePieces, Collection<PeerConnection> peers);
}
