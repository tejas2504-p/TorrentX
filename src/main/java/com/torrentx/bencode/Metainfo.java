package com.torrentx.bencode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Metainfo {
    private final String announce;
    private final List<List<String>> announceList;
    private final byte[] infoHash;
    private final String name;
    private final long pieceLength;
    private final List<byte[]> pieceHashes;
    private final List<MetainfoFile> files;
    private final long totalLength;
    private final boolean isMultiFile;

    public Metainfo(Map<String, Object> torrentMap, byte[] rawInfoBytes) throws BencodeException {
        if (torrentMap == null || rawInfoBytes == null) {
            throw new BencodeException("Invalid dictionary structure");
        }

        // Announce URL
        Object announceVal = torrentMap.get("announce");
        if (announceVal instanceof byte[]) {
            this.announce = new String((byte[]) announceVal, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            this.announce = "";
        }

        // Announce list (optional)
        this.announceList = new ArrayList<>();
        Object announceListVal = torrentMap.get("announce-list");
        if (announceListVal instanceof List) {
            for (Object tierObj : (List<?>) announceListVal) {
                if (tierObj instanceof List) {
                    List<String> tier = new ArrayList<>();
                    for (Object urlObj : (List<?>) tierObj) {
                        if (urlObj instanceof byte[]) {
                            tier.add(new String((byte[]) urlObj, java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                    if (!tier.isEmpty()) {
                        this.announceList.add(tier);
                    }
                }
            }
        }

        // Compute info hash
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            this.infoHash = digest.digest(rawInfoBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 digest not available", e);
        }

        // Parse info dictionary
        Object infoObj = torrentMap.get("info");
        if (!(infoObj instanceof Map)) {
            throw new BencodeException("Torrent missing info dictionary");
        }
        Map<?, ?> infoMap = (Map<?, ?>) infoObj;

        // Name (suggested folder/file name)
        Object nameVal = infoMap.get("name");
        if (nameVal instanceof byte[]) {
            this.name = new String((byte[]) nameVal, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            throw new BencodeException("Info dictionary missing name");
        }

        // Piece length
        Object pieceLengthVal = infoMap.get("piece length");
        if (pieceLengthVal instanceof Long) {
            this.pieceLength = (Long) pieceLengthVal;
        } else {
            throw new BencodeException("Info dictionary missing piece length");
        }

        // Pieces (SHA-1 hashes concatenated)
        Object piecesVal = infoMap.get("pieces");
        if (!(piecesVal instanceof byte[])) {
            throw new BencodeException("Info dictionary missing pieces byte string");
        }
        byte[] piecesBytes = (byte[]) piecesVal;
        if (piecesBytes.length % 20 != 0) {
            throw new BencodeException("Pieces string length must be a multiple of 20");
        }
        int numPieces = piecesBytes.length / 20;
        this.pieceHashes = new ArrayList<>(numPieces);
        for (int i = 0; i < numPieces; i++) {
            byte[] hash = new byte[20];
            System.arraycopy(piecesBytes, i * 20, hash, 0, 20);
            this.pieceHashes.add(hash);
        }

        // Single vs Multi-file
        this.files = new ArrayList<>();
        Object filesVal = infoMap.get("files");
        if (filesVal instanceof List) {
            // Multi-file
            this.isMultiFile = true;
            long accumLength = 0;
            for (Object fileObj : (List<?>) filesVal) {
                if (!(fileObj instanceof Map)) {
                    throw new BencodeException("File entry is not a dictionary in files list");
                }
                Map<?, ?> fileMap = (Map<?, ?>) fileObj;
                
                Object fileLengthVal = fileMap.get("length");
                if (!(fileLengthVal instanceof Long)) {
                    throw new BencodeException("File entry missing length");
                }
                long fileLength = (Long) fileLengthVal;
                
                Object pathVal = fileMap.get("path");
                if (!(pathVal instanceof List)) {
                    throw new BencodeException("File entry missing path");
                }
                List<String> pathList = new ArrayList<>();
                for (Object pathPartObj : (List<?>) pathVal) {
                    if (pathPartObj instanceof byte[]) {
                        pathList.add(new String((byte[]) pathPartObj, java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                if (pathList.isEmpty()) {
                    throw new BencodeException("File entry path list is empty");
                }
                
                this.files.add(new MetainfoFile(fileLength, pathList));
                accumLength += fileLength;
            }
            this.totalLength = accumLength;
        } else {
            // Single-file
            this.isMultiFile = false;
            Object lengthVal = infoMap.get("length");
            if (!(lengthVal instanceof Long)) {
                throw new BencodeException("Info dictionary missing file length for single file");
            }
            this.totalLength = (Long) lengthVal;
            this.files.add(new MetainfoFile(this.totalLength, Arrays.asList(this.name)));
        }
    }

    public String getAnnounce() {
        return announce;
    }

    public List<List<String>> getAnnounceList() {
        return announceList;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public String getInfoHashHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : infoHash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String getName() {
        return name;
    }

    public long getPieceLength() {
        return pieceLength;
    }

    public List<byte[]> getPieceHashes() {
        return pieceHashes;
    }

    public int getPieceCount() {
        return pieceHashes.size();
    }

    public List<MetainfoFile> getFiles() {
        return files;
    }

    public long getTotalLength() {
        return totalLength;
    }

    public boolean isMultiFile() {
        return isMultiFile;
    }
}
