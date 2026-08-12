package com.torrentx.bencode;

import java.util.List;

public class MetainfoFile {
    private final long length;
    private final List<String> path;

    public MetainfoFile(long length, List<String> path) {
        this.length = length;
        this.path = path;
    }

    public long getLength() {
        return length;
    }

    public List<String> getPath() {
        return path;
    }

    public String getFullPath() {
        return String.join("/", path);
    }
}
