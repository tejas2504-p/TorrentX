package com.torrentx.storage;

import java.io.File;

public class FileSpan {
    private final File file;
    private final long startOffset;
    private final long length;

    public FileSpan(File file, long startOffset, long length) {
        this.file = file;
        this.startOffset = startOffset;
        this.length = length;
    }

    public File getFile() {
        return file;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public long getLength() {
        return length;
    }

    public long getEndOffset() {
        return startOffset + length;
    }
}
