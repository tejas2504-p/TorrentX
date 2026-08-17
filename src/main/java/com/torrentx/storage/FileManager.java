package com.torrentx.storage;

import com.torrentx.bencode.Metainfo;
import com.torrentx.bencode.MetainfoFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileManager {
    private final List<FileSpan> fileSpans;
    private final long totalLength;
    private final Map<File, RandomAccessFile> openFiles;

    public FileManager(File baseDir, Metainfo metainfo) {
        this.fileSpans = new ArrayList<>();
        this.openFiles = new HashMap<>();
        long currentOffset = 0;
        
        File torrentDir = baseDir;
        if (metainfo.isMultiFile()) {
            torrentDir = new File(baseDir, metainfo.getName());
        }
        
        for (MetainfoFile mFile : metainfo.getFiles()) {
            File targetFile = torrentDir;
            for (String part : mFile.getPath()) {
                targetFile = new File(targetFile, part);
            }
            fileSpans.add(new FileSpan(targetFile, currentOffset, mFile.getLength()));
            currentOffset += mFile.getLength();
        }
        
        this.totalLength = currentOffset;
    }

    public synchronized void allocate() throws IOException {
        for (FileSpan span : fileSpans) {
            File file = span.getFile();
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    raf.setLength(span.getLength());
                }
            }
        }
    }

    public synchronized void write(long globalOffset, byte[] data) throws IOException {
        if (globalOffset + data.length > totalLength) {
            throw new IllegalArgumentException("Write out of bounds: offset=" + globalOffset + ", length=" + data.length);
        }

        int bytesWritten = 0;
        int bytesRemaining = data.length;

        for (FileSpan span : fileSpans) {
            if (bytesRemaining <= 0) {
                break;
            }

            long spanStart = span.getStartOffset();
            long spanEnd = span.getEndOffset();
            long currentPos = globalOffset + bytesWritten;

            if (currentPos >= spanStart && currentPos < spanEnd) {
                long fileOffset = currentPos - spanStart;
                long bytesToCopy = Math.min(bytesRemaining, spanEnd - currentPos);

                RandomAccessFile raf = getOrOpenFile(span.getFile());
                raf.seek(fileOffset);
                raf.write(data, bytesWritten, (int) bytesToCopy);

                bytesWritten += bytesToCopy;
                bytesRemaining -= bytesToCopy;
            }
        }
    }

    public synchronized byte[] read(long globalOffset, int length) throws IOException {
        if (globalOffset + length > totalLength) {
            throw new IllegalArgumentException("Read out of bounds: offset=" + globalOffset + ", length=" + length);
        }

        byte[] buffer = new byte[length];
        int bytesRead = 0;
        int bytesRemaining = length;

        for (FileSpan span : fileSpans) {
            if (bytesRemaining <= 0) {
                break;
            }

            long spanStart = span.getStartOffset();
            long spanEnd = span.getEndOffset();
            long currentPos = globalOffset + bytesRead;

            if (currentPos >= spanStart && currentPos < spanEnd) {
                long fileOffset = currentPos - spanStart;
                long bytesToCopy = Math.min(bytesRemaining, spanEnd - currentPos);

                RandomAccessFile raf = getOrOpenFile(span.getFile());
                raf.seek(fileOffset);
                raf.readFully(buffer, bytesRead, (int) bytesToCopy);

                bytesRead += bytesToCopy;
                bytesRemaining -= bytesToCopy;
            }
        }

        return buffer;
    }

    private RandomAccessFile getOrOpenFile(File file) throws IOException {
        RandomAccessFile raf = openFiles.get(file);
        if (raf == null) {
            raf = new RandomAccessFile(file, "rw");
            openFiles.put(file, raf);
        }
        return raf;
    }

    public synchronized void close() throws IOException {
        for (RandomAccessFile raf : openFiles.values()) {
            if (raf != null) {
                raf.close();
            }
        }
        openFiles.clear();
    }
}
