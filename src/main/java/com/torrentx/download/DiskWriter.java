package com.torrentx.download;

import com.torrentx.torrent.TorrentFile;
import com.torrentx.torrent.TorrentMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles writing verified piece data to disk.
 * Supports multi-file torrents and prevents path traversal vulnerabilities.
 */
public class DiskWriter implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DiskWriter.class.getName());
    
    private final TorrentMetadata metadata;
    private final Path baseDownloadDir;
    
    private static class FileMapping {
        final Path absolutePath;
        final long startOffset; // Global start offset across the whole torrent
        final long length;
        
        FileMapping(Path absolutePath, long startOffset, long length) {
            this.absolutePath = absolutePath;
            this.startOffset = startOffset;
            this.length = length;
        }
    }
    
    private final List<FileMapping> fileMappings;
    
    public DiskWriter(TorrentMetadata metadata, Path baseDownloadDir) throws IOException {
        if (metadata == null || baseDownloadDir == null) {
            throw new IllegalArgumentException("Metadata and base directory cannot be null");
        }
        this.metadata = metadata;
        this.baseDownloadDir = baseDownloadDir.toAbsolutePath().normalize();
        
        Files.createDirectories(this.baseDownloadDir);
        
        this.fileMappings = new ArrayList<>();
        long currentGlobalOffset = 0;
        
        for (TorrentFile torrentFile : metadata.getFiles()) {
            Path filePath = this.baseDownloadDir;
            if (!metadata.isSingleFile()) {
                filePath = filePath.resolve(metadata.getName());
            }
            
            for (String segment : torrentFile.getPath()) {
                filePath = filePath.resolve(segment);
            }
            filePath = filePath.toAbsolutePath().normalize();
            
            if (!filePath.startsWith(this.baseDownloadDir)) {
                throw new IOException("Path traversal detected! Attempted to write to: " + filePath);
            }
            
            fileMappings.add(new FileMapping(filePath, currentGlobalOffset, torrentFile.getLength()));
            currentGlobalOffset += torrentFile.getLength();
        }
    }
    
    public void writePiece(int pieceIndex, byte[] data) throws IOException {
        if (pieceIndex < 0 || pieceIndex >= metadata.getPieceCount()) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }
        
        long globalStart = pieceIndex * metadata.getPieceLength();
        long globalEnd = globalStart + data.length;
        
        if (globalEnd > metadata.getTotalLength()) {
            throw new IllegalArgumentException("Piece data exceeds total torrent length");
        }
        
        int bytesWritten = 0;
        
        for (FileMapping mapping : fileMappings) {
            long mappingEnd = mapping.startOffset + mapping.length;
            
            if (globalStart < mappingEnd && globalEnd > mapping.startOffset) {
                long intersectStart = Math.max(globalStart, mapping.startOffset);
                long intersectEnd = Math.min(globalEnd, mappingEnd);
                
                long fileLocalOffset = intersectStart - mapping.startOffset;
                int pieceLocalOffset = (int) (intersectStart - globalStart);
                int lengthToWrite = (int) (intersectEnd - intersectStart);
                
                Files.createDirectories(mapping.absolutePath.getParent());
                
                try (FileChannel channel = FileChannel.open(mapping.absolutePath, 
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(data, pieceLocalOffset, lengthToWrite);
                    long currentFileOffset = fileLocalOffset;
                    while (buffer.hasRemaining()) {
                        int written = channel.write(buffer, currentFileOffset);
                        if (written <= 0) {
                            throw new IOException("Failed to write data to file channel");
                        }
                        currentFileOffset += written;
                    }
                }
                
                bytesWritten += lengthToWrite;
                if (bytesWritten == data.length) {
                    break;
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        // Future optimization: cache and close FileChannels instead of opening per write
    }
}
