package com.torrentx.torrent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parser responsible for reading .torrent files and deserializing them into metadata models.
 */
public class TorrentParser {

    /**
     * Parses a local torrent file by path.
     *
     * @param filePath the path to the torrent file.
     * @return the parsed TorrentMetadata.
     * @throws TorrentException if parsing fails.
     */
    public TorrentMetadata parse(String filePath) throws TorrentException {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        return parse(new File(filePath));
    }

    /**
     * Parses a local torrent file by Path.
     *
     * @param path the path to the torrent file.
     * @return the parsed TorrentMetadata.
     * @throws TorrentException if parsing fails.
     */
    public TorrentMetadata parse(java.nio.file.Path path) throws TorrentException {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        return parse(path.toFile());
    }

    /**
     * Parses a local torrent file.
     *
     * @param torrentFile the .torrent file.
     * @return the parsed TorrentMetadata.
     * @throws TorrentException if parsing fails.
     */
    public TorrentMetadata parse(File torrentFile) throws TorrentException {
        if (torrentFile == null) {
            throw new IllegalArgumentException("Torrent file cannot be null");
        }
        if (!torrentFile.exists()) {
            throw new TorrentException("Torrent file does not exist: " + torrentFile.getAbsolutePath());
        }
        if (!torrentFile.isFile()) {
            throw new TorrentException("Path is not a regular file: " + torrentFile.getAbsolutePath());
        }
        try {
            byte[] bytes = Files.readAllBytes(torrentFile.toPath());
            return parse(bytes);
        } catch (IOException e) {
            throw new TorrentException("Failed to read torrent file: " + torrentFile.getName(), e);
        }
    }

    /**
     * Parses raw bencoded torrent bytes.
     *
     * @param torrentData the raw bytes of the torrent.
     * @return the parsed TorrentMetadata.
     * @throws TorrentException if parsing fails.
     */
    @SuppressWarnings("unchecked")
    public TorrentMetadata metals = null;
    
    @SuppressWarnings("unchecked")
    public TorrentMetadata parse(byte[] torrentData) throws TorrentException {
        if (torrentData == null) {
            throw new IllegalArgumentException("Torrent data cannot be null");
        }
        
        BencodeDecoder decoder = new BencodeDecoder(torrentData);
        Object decoded;
        try {
            decoded = decoder.decode();
        } catch (BencodeException e) {
            throw new TorrentException("Malformed Bencode content", e);
        }
        
        if (!(decoded instanceof Map)) {
            throw new TorrentException("Root of torrent file must be a Bencode dictionary");
        }
        
        Map<String, Object> root = (Map<String, Object>) decoded;
        
        // Extract & validate announce URL
        Object announceObj = root.get("announce");
        if (announceObj == null) {
            throw new TorrentException("Missing required 'announce' field");
        }
        if (!(announceObj instanceof byte[])) {
            throw new TorrentException("Invalid 'announce' field: must be a byte string");
        }
        String announce = new String((byte[]) announceObj, StandardCharsets.UTF_8);
        if (announce.trim().isEmpty()) {
            throw new TorrentException("Announce URL cannot be empty");
        }
        
        // Extract & validate announce list (optional, but must be correctly structured if present)
        List<List<String>> announceList = new ArrayList<>();
        if (root.containsKey("announce-list")) {
            Object announceListObj = root.get("announce-list");
            if (!(announceListObj instanceof List)) {
                throw new TorrentException("announce-list field must be a Bencode list");
            }
            for (Object tierObj : (List<?>) announceListObj) {
                if (!(tierObj instanceof List)) {
                    throw new TorrentException("Each tier in announce-list must be a Bencode list");
                }
                List<String> tier = new ArrayList<>();
                for (Object urlObj : (List<?>) tierObj) {
                    if (!(urlObj instanceof byte[])) {
                        throw new TorrentException("Each tracker URL in announce-list must be a Bencode byte string");
                    }
                    tier.add(new String((byte[]) urlObj, StandardCharsets.UTF_8));
                }
                if (!tier.isEmpty()) {
                    announceList.add(tier);
                }
            }
        }
        
        // Extract info block
        Object infoObj = root.get("info");
        if (!(infoObj instanceof Map)) {
            throw new TorrentException("Missing or invalid 'info' dictionary in torrent");
        }
        
        Map<String, Object> info = (Map<String, Object>) infoObj;
        
        // Calculate info hash
        byte[] rawInfoBytes = decoder.getRawInfoBytes();
        if (rawInfoBytes == null) {
            throw new TorrentException("Failed to extract raw 'info' dictionary bytes");
        }
        
        byte[] infoHash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            infoHash = md.digest(rawInfoBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new TorrentException("SHA-1 message digest algorithm not available", e);
        }
        
        // Parse piece length
        Object pieceLengthObj = info.get("piece length");
        if (pieceLengthObj == null) {
            throw new TorrentException("Missing required 'piece length' field in info");
        }
        long pieceLength = getLongValue(info, "piece length");
        if (pieceLength <= 0) {
            throw new TorrentException("Piece length must be a positive integer");
        }
        
        // Parse pieces hash array
        Object piecesObj = info.get("pieces");
        if (!(piecesObj instanceof byte[])) {
            throw new TorrentException("Missing or invalid 'pieces' field in info");
        }
        byte[] pieces = (byte[]) piecesObj;
        if (pieces.length % 20 != 0) {
            throw new TorrentException("Pieces byte array length must be a multiple of 20");
        }
        
        // Parse name
        Object nameObj = info.get("name");
        if (!(nameObj instanceof byte[])) {
            throw new TorrentException("Missing or invalid 'name' field in info");
        }
        byte[] rawName = (byte[]) nameObj;
        String name = new String(rawName, StandardCharsets.UTF_8);
        
        // Parse single or multi file layout
        List<TorrentFile> torrentFiles = new ArrayList<>();
        boolean isSingleFile;
        
        if (info.containsKey("files")) {
            isSingleFile = false;
            Object filesObj = info.get("files");
            if (!(filesObj instanceof List)) {
                throw new TorrentException("files field must be a list in multi-file torrent");
            }
            List<?> filesList = (List<?>) filesObj;
            if (filesList.isEmpty()) {
                throw new TorrentException("files list cannot be empty in multi-file torrent");
            }
            for (Object fileObj : filesList) {
                if (!(fileObj instanceof Map)) {
                    throw new TorrentException("Each file entry in files list must be a dictionary");
                }
                Map<String, Object> fileMap = (Map<String, Object>) fileObj;
                
                Object lengthObj = fileMap.get("length");
                if (lengthObj == null) {
                    throw new TorrentException("Missing file length in files list entry");
                }
                long fileLength = getLongValue(fileMap, "length");
                if (fileLength < 0) {
                    throw new TorrentException("File length cannot be negative");
                }
                
                Object pathObj = fileMap.get("path");
                if (!(pathObj instanceof List)) {
                    throw new TorrentException("path field in files list entry must be a list");
                }
                List<byte[]> rawPath = new ArrayList<>();
                for (Object segment : (List<?>) pathObj) {
                    if (!(segment instanceof byte[])) {
                        throw new TorrentException("path segments must be byte strings");
                    }
                    rawPath.add((byte[]) segment);
                }
                if (rawPath.isEmpty()) {
                    throw new TorrentException("path segments cannot be empty");
                }
                torrentFiles.add(new TorrentFile(fileLength, rawPath));
            }
        } else {
            isSingleFile = true;
            Object lengthObj = info.get("length");
            if (lengthObj == null) {
                throw new TorrentException("Missing required 'length' field in info for single-file torrent");
            }
            long fileLength = getLongValue(info, "length");
            if (fileLength < 0) {
                throw new TorrentException("File length cannot be negative");
            }
            List<byte[]> rawPath = Collections.singletonList(rawName);
            torrentFiles.add(new TorrentFile(fileLength, rawPath));
        }
        
        // Calculate and validate total length
        long totalLength = 0;
        if (isSingleFile) {
            totalLength = torrentFiles.get(0).getLength();
        } else {
            for (TorrentFile f : torrentFiles) {
                totalLength += f.getLength();
            }
        }
        
        if (totalLength <= 0) {
            throw new TorrentException("Total length must be positive: " + totalLength);
        }
        
        // Calculate the expected number of pieces and verify consistency
        long expectedPieceCount = (totalLength + pieceLength - 1) / pieceLength;
        int parsedPieceCount = pieces.length / 20;
        if (parsedPieceCount != expectedPieceCount) {
            throw new TorrentException("Inconsistent metadata: pieces count (" + parsedPieceCount + 
                                       ") does not match expected piece count (" + expectedPieceCount + 
                                       ") calculated from total length " + totalLength + " and piece length " + pieceLength);
        }
        
        return new TorrentMetadata(announce, announceList, name, rawName, pieceLength, pieces, torrentFiles, isSingleFile, infoHash);
    }
    
    private long getLongValue(Map<String, Object> map, String key) throws TorrentException {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        throw new TorrentException("Missing or invalid numeric field: '" + key + "'");
    }
}
