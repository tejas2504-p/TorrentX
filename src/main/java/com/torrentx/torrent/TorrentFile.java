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
        this.length = length;
        this.rawPath = rawPath != null ? Collections.unmodifiableList(new ArrayList<>(rawPath)) : Collections.emptyList();
        
        List<String> parsedPath = new ArrayList<>();
        if (rawPath != null) {
            for (byte[] segment : rawPath) {
                parsedPath.add(new String(segment, StandardCharsets.UTF_8));
            }
        }
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