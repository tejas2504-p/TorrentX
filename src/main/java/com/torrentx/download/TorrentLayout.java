package com.torrentx.download;

import com.torrentx.torrent.TorrentMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calculates and manages the layout of pieces and blocks for a torrent.
 */
public class TorrentLayout {
    private final List<Piece> pieces;
    private final int totalPieces;
    private final long totalLength;
    private final long pieceLength;
    private final int standardBlockSize;

    public TorrentLayout(TorrentMetadata metadata, int standardBlockSize) {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        if (standardBlockSize <= 0) {
            throw new IllegalArgumentException("Standard block size must be positive");
        }
        
        this.totalLength = metadata.getTotalLength();
        this.pieceLength = metadata.getPieceLength();
        this.standardBlockSize = standardBlockSize;
        
        long calculatedPieceCount = (this.totalLength + this.pieceLength - 1) / this.pieceLength;
        if (calculatedPieceCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Too many pieces in this torrent");
        }
        this.totalPieces = (int) calculatedPieceCount;
        
        if (this.totalPieces != metadata.getPieceCount()) {
            throw new IllegalArgumentException("Inconsistent metadata: piece count mismatch. Expected " 
                + this.totalPieces + " but metadata has " + metadata.getPieceCount());
        }
        
        List<Piece> tempPieces = new ArrayList<>(this.totalPieces);
        
        for (int i = 0; i < this.totalPieces; i++) {
            byte[] expectedHash = metadata.getPieceHash(i);
            
            int currentPieceLength = (int) this.pieceLength;
            if (i == this.totalPieces - 1) {
                long remainder = this.totalLength % this.pieceLength;
                if (remainder != 0) {
                    currentPieceLength = (int) remainder;
                }
            }
            
            tempPieces.add(new Piece(i, currentPieceLength, expectedHash, standardBlockSize));
        }
        
        this.pieces = Collections.unmodifiableList(tempPieces);
    }
    
    public List<Piece> getPieces() {
        return pieces;
    }
    
    public Piece getPiece(int index) {
        if (index < 0 || index >= pieces.size()) {
            throw new IndexOutOfBoundsException("Invalid piece index: " + index);
        }
        return pieces.get(index);
    }
    
    public int getTotalPieces() {
        return totalPieces;
    }

    public long getTotalLength() {
        return totalLength;
    }

    public long getPieceLength() {
        return pieceLength;
    }

    public int getStandardBlockSize() {
        return standardBlockSize;
    }
}
