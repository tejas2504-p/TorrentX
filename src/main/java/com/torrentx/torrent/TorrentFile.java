package com.torrentx.torrent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a file mapping entry in a torrent metadata configuration.
 */
public class TorrentFile {
    private final long length;
    private final List<byte[]> rawPath;
    private final List<String> path;

    public TorrentFile(long length, List<byte[]> rawPath) {
        if (length < 0) {
            throw new IllegalArgumentException("File length cannot be negative");
        }
        if (rawPath == null || rawPath.isEmpty()) {
            throw new IllegalArgumentException("File path components cannot be null or empty");
        }
        this.length = length;
        
        List<byte[]> rawCopy = new ArrayList<>();
        List<String> parsedPath = new ArrayList<>();
        
        for (byte[] segment : rawPath) {
            if (segment == null || segment.length == 0) {
                throw new IllegalArgumentException("File path segment cannot be null or empty");
            }
            String segmentStr = new String(segment, StandardCharsets.UTF_8).trim();
            if (segmentStr.isEmpty()) {
                throw new IllegalArgumentException("File path segment cannot be blank");
            }
            if ("..".equals(segmentStr)) {
                throw new IllegalArgumentException("File path traversal segment '..' is not allowed");
            }
            if (".".equals(segmentStr)) {
                throw new IllegalArgumentException("File path segment '.' is not allowed");
            }
            rawCopy.add(segment.clone());
            parsedPath.add(segmentStr);
        }
        
        this.rawPath = Collections.unmodifiableList(rawCopy);
        this.path = Collections.unmodifiableList(parsedPath);
    }

    public long getLength() {
        return length;
    }

    public List<byte[]> getRawPath() {
        return rawPath;
    }

    public List<String> getPath() {
        return path;
    }
}
