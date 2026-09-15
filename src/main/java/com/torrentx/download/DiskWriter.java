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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<Path, FileChannel> openChannels;
    
    public DiskWriter(TorrentMetadata metadata, Path baseDownloadDir) throws IOException {
        if (metadata == null || baseDownloadDir == null) {
            throw new IllegalArgumentException("Metadata and base directory cannot be null");
        }
        this.metadata = metadata;
        this.baseDownloadDir = baseDownloadDir.toAbsolutePath().normalize();
        
        Files.createDirectories(this.baseDownloadDir);
        
        this.fileMappings = new ArrayList<>();
        this.openChannels = new ConcurrentHashMap<>();
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
    
    private FileChannel getChannel(Path path) throws IOException {
        return openChannels.computeIfAbsent(path, p -> {
            try {
                Files.createDirectories(p.getParent());
                return FileChannel.open(p, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open file channel for: " + p, e);
            }
        });
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
                
                FileChannel channel = getChannel(mapping.absolutePath);
                ByteBuffer buffer = ByteBuffer.wrap(data, pieceLocalOffset, lengthToWrite);
                long currentFileOffset = fileLocalOffset;
                while (buffer.hasRemaining()) {
                    int written = channel.write(buffer, currentFileOffset);
                    if (written <= 0) {
                        throw new IOException("Failed to write data to file channel");
                    }
                    currentFileOffset += written;
                }
                
                bytesWritten += lengthToWrite;
                if (bytesWritten == data.length) {
                    break;
                }
            }
        }
    }

    public byte[] readBlock(int pieceIndex, int offset, int length) throws IOException {
        if (pieceIndex < 0 || pieceIndex >= metadata.getPieceCount()) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }
        
        long globalStart = (pieceIndex * metadata.getPieceLength()) + offset;
        long globalEnd = globalStart + length;
        
        if (globalEnd > metadata.getTotalLength()) {
            throw new IllegalArgumentException("Requested block exceeds total torrent length");
        }
        
        byte[] blockData = new byte[length];
        int bytesRead = 0;
        
        for (FileMapping mapping : fileMappings) {
            long mappingEnd = mapping.startOffset + mapping.length;
            
            if (globalStart < mappingEnd && globalEnd > mapping.startOffset) {
                long intersectStart = Math.max(globalStart, mapping.startOffset);
                long intersectEnd = Math.min(globalEnd, mappingEnd);
                
                long fileLocalOffset = intersectStart - mapping.startOffset;
                int blockLocalOffset = (int) (intersectStart - globalStart);
                int lengthToRead = (int) (intersectEnd - intersectStart);
                
                FileChannel channel = getChannel(mapping.absolutePath);
                ByteBuffer buffer = ByteBuffer.wrap(blockData, blockLocalOffset, lengthToRead);
                long currentFileOffset = fileLocalOffset;
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer, currentFileOffset);
                    if (read < 0) {
                        throw new IOException("Unexpected end of file reached while reading");
                    }
                    currentFileOffset += read;
                }
                
                bytesRead += lengthToRead;
                if (bytesRead == length) {
                    break;
                }
            }
        }
        return blockData;
    }

    @Override
    public void close() throws Exception {
        for (FileChannel channel : openChannels.values()) {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
        openChannels.clear();
    }
}
