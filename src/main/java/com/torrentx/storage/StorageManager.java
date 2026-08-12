package com.torrentx.storage;

import com.torrentx.bencode.Metainfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class StorageManager {
    private static final Logger logger = LoggerFactory.getLogger(StorageManager.class);

    private final Metainfo metainfo;
    private final FileManager fileManager;

    public StorageManager(File baseDir, Metainfo metainfo) {
        this.metainfo = metainfo;
        this.fileManager = new FileManager(baseDir, metainfo);
    }

    public void initialize() throws IOException {
        fileManager.allocate();
    }

    public void writeBlock(int pieceIndex, int blockOffset, byte[] data) throws IOException {
        long globalOffset = pieceIndex * metainfo.getPieceLength() + blockOffset;
        fileManager.write(globalOffset, data);
    }

    public byte[] readBlock(int pieceIndex, int blockOffset, int length) throws IOException {
        long globalOffset = pieceIndex * metainfo.getPieceLength() + blockOffset;
        return fileManager.read(globalOffset, length);
    }

    public boolean verifyPiece(int pieceIndex) {
        try {
            long pieceLength = getPieceLength(pieceIndex);
            long globalOffset = pieceIndex * metainfo.getPieceLength();
            
            byte[] pieceData = fileManager.read(globalOffset, (int) pieceLength);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] computedHash = digest.digest(pieceData);
            byte[] expectedHash = metainfo.getPieceHashes().get(pieceIndex);
            
            return Arrays.equals(computedHash, expectedHash);
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Error verifying piece index {}", pieceIndex, e);
            return false;
        }
    }

    public long getPieceLength(int pieceIndex) {
        int pieceCount = metainfo.getPieceCount();
        if (pieceIndex < 0 || pieceIndex >= pieceCount) {
            throw new IllegalArgumentException("Invalid piece index " + pieceIndex);
        }
        if (pieceIndex == pieceCount - 1) {
            long remainder = metainfo.getTotalLength() % metainfo.getPieceLength();
            return remainder == 0 ? metainfo.getPieceLength() : remainder;
        }
        return metainfo.getPieceLength();
    }
}
