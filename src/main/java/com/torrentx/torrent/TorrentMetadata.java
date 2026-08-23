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
        // Validate announce (when present)
        if (announce != null && announce.trim().isEmpty()) {
            throw new IllegalArgumentException("Announce URL cannot be empty or blank when present");
        }
        this.announce = announce;
        
        // Validate announceList (when present)
        if (announceList != null) {
            for (List<String> tier : announceList) {
                if (tier == null) {
                    throw new IllegalArgumentException("Announce list tier cannot be null");
                }
                for (String url : tier) {
                    if (url == null || url.trim().isEmpty()) {
                        throw new IllegalArgumentException("Tracker URL in announce list cannot be null or blank");
                    }
                }
            }
            this.announceList = Collections.unmodifiableList(new ArrayList<>(announceList));
        } else {
            this.announceList = Collections.emptyList();
        }
        
        // Validate name and rawName
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Torrent name cannot be null or empty");
        }
        String nameTrimmed = name.trim();
        if ("..".equals(nameTrimmed) || ".".equals(nameTrimmed)) {
            throw new IllegalArgumentException("Torrent name is invalid (directory traversal or relative path)");
        }
        this.name = name;
        
        if (rawName == null || rawName.length == 0) {
            throw new IllegalArgumentException("Raw name cannot be null or empty");
        }
        this.rawName = rawName.clone();
        
        // Validate pieceLength
        if (pieceLength <= 0) {
            throw new IllegalArgumentException("Piece length must be a positive integer: " + pieceLength);
        }
        this.pieceLength = pieceLength;
        
        // Validate pieces
        if (pieces == null) {
            throw new IllegalArgumentException("Pieces cannot be null");
        }
        if (pieces.length % 20 != 0) {
            throw new IllegalArgumentException("Pieces length must be a multiple of 20: " + pieces.length);
        }
        this.pieces = pieces.clone();
        
        // Validate infoHash
        if (infoHash == null) {
            throw new IllegalArgumentException("Info-hash cannot be null");
        }
        if (infoHash.length != 20) {
            throw new IllegalArgumentException("Info-hash must be exactly 20 bytes: " + infoHash.length);
        }
        this.infoHash = infoHash.clone();
        
        // Validate files list
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be null or empty");
        }
        
        // Validate each file
        for (TorrentFile f : files) {
            if (f == null) {
                throw new IllegalArgumentException("Torrent file entry cannot be null");
            }
            if (f.getLength() < 0) {
                throw new IllegalArgumentException("File length cannot be negative: " + f.getLength());
            }
            List<byte[]> rawPath = f.getRawPath();
            if (rawPath == null || rawPath.isEmpty()) {
                throw new IllegalArgumentException("File path components cannot be null or empty");
            }
            for (byte[] segmentBytes : rawPath) {
                if (segmentBytes == null || segmentBytes.length == 0) {
                    throw new IllegalArgumentException("File path segment cannot be null or empty");
                }
                String segmentStr = new String(segmentBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (segmentStr.isEmpty()) {
                    throw new IllegalArgumentException("File path segment cannot be blank");
                }
                if ("..".equals(segmentStr)) {
                    throw new IllegalArgumentException("File path traversal segment '..' is not allowed");
                }
                if (".".equals(segmentStr)) {
                    throw new IllegalArgumentException("File path segment '.' is not allowed");
                }
            }
        }
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
        this.isSingleFile = isSingleFile;
        
        // Calculate and validate total length
        long calculatedTotalLength = 0;
        for (TorrentFile f : files) {
            calculatedTotalLength += f.getLength();
        }
        if (calculatedTotalLength <= 0) {
            throw new IllegalArgumentException("Total torrent length must be positive: " + calculatedTotalLength);
        }
        this.totalLength = calculatedTotalLength;
        
        // Verify piece count consistency
        long expectedPieceCount = (calculatedTotalLength + pieceLength - 1) / pieceLength;
        int parsedPieceCount = pieces.length / 20;
        if (parsedPieceCount != expectedPieceCount) {
            throw new IllegalArgumentException("Inconsistent metadata: pieces count (" + parsedPieceCount + 
                                               ") does not match expected piece count (" + expectedPieceCount + 
                                               ") calculated from total length " + calculatedTotalLength + " and piece length " + pieceLength);
        }
    }

    /**
     * Compatibility constructor matching Phase 1 signature.
     */
    public TorrentMetadata(String announce, byte[] infoHash, String name, long pieceLength, List<byte[]> pieces, long totalLength) {
        this(announce, null, name, name != null ? name.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0], 
             pieceLength, listToBytes(pieces), 
             Collections.singletonList(new TorrentFile(totalLength, Collections.singletonList(name != null ? name.getBytes(java.nio.charset.StandardCharsets.UTF_8) : "default_name".getBytes(java.nio.charset.StandardCharsets.UTF_8)))), 
             true, infoHash);
    }

    private static byte[] listToBytes(List<byte[]> list) {
        if (list == null) {
            return new byte[0];
        }
        byte[] bytes = new byte[list.size() * 20];
        for (int i = 0; i < list.size(); i++) {
            byte[] p = list.get(i);
            if (p == null) {
                throw new IllegalArgumentException("Piece hash entry cannot be null");
            }
            if (p.length != 20) {
                throw new IllegalArgumentException("Each piece hash must be exactly 20 bytes: " + p.length);
            }
            System.arraycopy(p, 0, bytes, i * 20, 20);
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

    public String getInfoHashHex() {
        if (infoHash == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(infoHash.length * 2);
        for (byte b : infoHash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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
