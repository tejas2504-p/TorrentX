package com.torrentx.storage;

/**
 * Interface representing the block storage layer.
 * Manages chunk offsets, random reads/writes, and verification hashes.
 */
public interface PieceStorage {

    /**
     * Writes a block of data to storage.
     *
     * @param pieceIndex the index of the piece.
     * @param offset the byte offset inside the piece.
     * @param data the block payload.
     * @throws Exception if disk writing fails.
     */
    void writeBlock(int pieceIndex, int offset, byte[] data) throws Exception;

    /**
     * Reads a block of data from storage.
     *
     * @param pieceIndex the index of the piece.
     * @param offset the byte offset inside the piece.
     * @param length the number of bytes to read.
     * @return the read data bytes.
     * @throws Exception if disk reading fails.
     */
    byte[] readBlock(int pieceIndex, int offset, int length) throws Exception;

    /**
     * Checks if a piece contains valid data by verifying its computed hash.
     *
     * @param pieceIndex the index of the piece.
     * @param expectedHash the SHA-1 checksum.
     * @return true if valid, false otherwise.
     */
    boolean verifyPiece(int pieceIndex, byte[] expectedHash);
}
