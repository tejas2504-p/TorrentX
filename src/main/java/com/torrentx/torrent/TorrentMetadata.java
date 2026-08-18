package com.torrentx.torrent;

import java.util.List;

/**
 * Immutable metadata representation of a parsed Torrent file (.torrent).
 */
public class TorrentMetadata {

    private final String announce;
    private final byte[] infoHash;
    private final String name;
    private final long pieceLength;
    private final List<byte[]> pieces;
    private final long totalLength;

    /**
     * Constructs TorrentMetadata with all required fields.
     */
    public TorrentMetadata(String announce, byte[] infoHash, String name, long pieceLength, List<byte[]> pieces, long totalLength) {
        this.announce = announce;
        this.infoHash = infoHash;
        this.name = name;
        this.pieceLength = pieceLength;
        this.pieces = pieces;
        this.totalLength = totalLength;
    }

    public String getAnnounce() {
        return announce;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public String getName() {
        return name;
    }

    public long getPieceLength() {
        return pieceLength;
    }

    public List<byte[]> getPieces() {
        return pieces;
    }

    public long getTotalLength() {
        return totalLength;
    }
}
