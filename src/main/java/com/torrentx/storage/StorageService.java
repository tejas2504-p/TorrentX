package com.torrentx.storage;

import java.io.IOException;

public interface StorageService extends AutoCloseable {
    void initialize() throws IOException;
    void writeBlock(int pieceIndex, int blockOffset, byte[] data) throws IOException;
    byte[] readBlock(int pieceIndex, int blockOffset, int length) throws IOException;
    boolean verifyPiece(int pieceIndex);
    long getPieceLength(int pieceIndex);
    void close() throws IOException;
}
