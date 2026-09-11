package com.torrentx.download;

import com.torrentx.tracker.PeerInfo;

/**
 * Strategy interface for selecting the optimal next piece to request from a peer.
 */
public interface PieceSelector {

    /**
     * Determines the optimal piece index to download next from a specified peer.
     *
     * @param peer         The peer to request from.
     * @param pieceManager The manager tracking current piece status and block requests.
     * @param availability The component tracking global piece availability/rarity.
     * @return The piece index to download, or -1 if no pieces are available/applicable.
     */
    int selectNextPiece(PeerInfo peer, PieceManager pieceManager, PieceAvailability availability);
}
