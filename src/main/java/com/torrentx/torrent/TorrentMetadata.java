package com.torrentx.torrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable metadata representation of a parsed Torrent file (.torrent).
 */
public class TorrentMetadata {

    private final String announce;
    private final List<List<String>> announceList;
    private final byte[] infoHash;
    private final String name;
    private final byte[] rawName;
    private final long pieceLength;
    private final byte[] pieces;
    private final List<TorrentFile> files;
    private final boolean isSingleFile;
    private final long totalLength;

    /**
     * Constructs TorrentMetadata with all required fields. (Full constructor)
     */
    public TorrentMetadata(String announce, List<List<String>> announceList, String name, byte[] rawName,
                           long pieceLength, byte[] pieces, List<TorrentFile> files, 
                           boolean isSingleFile, byte[] infoHash) {
        this.announce = announce;
        this.announceList = announceList != null ? announceList : Collections.emptyList();
        this.name = name;
        this.rawName = rawName != null ? rawName.clone() : new byte[0];
        this.pieceLength = pieceLength;
        this.pieces = pieces != null ? pieces.clone() : new byte[0];
        this.files = files != null ? Collections.unmodifiableList(new ArrayList<>(files)) : Collections.emptyList();
        this.isSingleFile = isSingleFile;
        this.infoHash = infoHash != null ? infoHash.clone() : new byte[0];
        
        long calculatedTotalLength = 0;
        if (isSingleFile && files != null && !files.isEmpty()) {
            calculatedTotalLength = files.get(0).getLength();
        } else if (files != null) {
            for (TorrentFile f : files) {
                calculatedTotalLength += f.getLength();
            }
        }
        this.totalLength = calculatedTotalLength;
    }

    /**
     * Compatibility constructor matching Phase 1 signature.
     */
    public TorrentMetadata(String announce, byte[] infoHash, String name, long pieceLength, List<byte[]> pieces, long totalLength) {
        this(announce, null, name, name != null ? name.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0], 
             pieceLength, listToBytes(pieces), 
             Collections.singletonList(new TorrentFile(totalLength, null)), true, infoHash);
    }

    private static byte[] listToBytes(List<byte[]> list) {
        if (list == null) {
            return new byte[0];
        }
        byte[] bytes = new byte[list.size() * 20];
        for (int i = 0; i < list.size(); i++) {
            byte[] p = list.get(i);
            if (p != null) {
                System.arraycopy(p, 0, bytes, i * 20, Math.min(p.length, 20));
            }
        }
        return bytes;
    }

    public String getAnnounce() {
        return announce;
    }

    public List<List<String>> getAnnounceList() {
        return announceList;
    }

    public byte[] getInfoHash() {
        return infoHash != null ? infoHash.clone() : null;
    }

    public String getName() {
        return name;
    }

    public byte[] getRawName() {
        return rawName != null ? rawName.clone() : null;
    }

    public long getPieceLength() {
        return pieceLength;
    }

    /**
     * Compatibility getter matching Phase 1 signature.
     */
    public List<byte[]> getPieces() {
        List<byte[]> list = new ArrayList<>();
        int count = getPieceCount();
        for (int i = 0; i < count; i++) {
            list.add(getPieceHash(i));
        }
        return list;
    }

    public byte[] getRawPieces() {
        return pieces != null ? pieces.clone() : null;
    }

    public int getPieceCount() {
        return pieces.length / 20;
    }

    public byte[] getPieceHash(int index) {
        if (index < 0 || index >= getPieceCount()) {
            throw new IndexOutOfBoundsException("Piece index out of bounds: " + index);
        }
        byte[] hash = new byte[20];
        System.arraycopy(pieces, index * 20, hash, 0, 20);
        return hash;
    }

    public List<TorrentFile> getFiles() {
        return files;
    }

    public boolean isSingleFile() {
        return isSingleFile;
    }

    public long getTotalLength() {
        return totalLength;
    }
}
