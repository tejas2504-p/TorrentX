package com.torrentx.download;

import com.torrentx.torrent.TorrentFile;
import com.torrentx.torrent.TorrentMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DiskWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void testSingleFilePieceWriting() throws Exception {
        TorrentMetadata metadata = new TorrentMetadata("http://tracker", new byte[20], 
            "single.txt", 100, Arrays.asList(new byte[20], new byte[20]), 150);
            
        try (DiskWriter writer = new DiskWriter(metadata, tempDir)) {
            byte[] data = new byte[100];
            Arrays.fill(data, (byte) 1);
            
            writer.writePiece(0, data);
            
            Path writtenFile = tempDir.resolve("single.txt");
            assertTrue(Files.exists(writtenFile));
            assertEquals(100, Files.size(writtenFile));
            
            byte[] readData = Files.readAllBytes(writtenFile);
            assertArrayEquals(data, readData);
        }
    }

    @Test
    void testMultiFilePieceCrossingBoundaries() throws Exception {
        TorrentFile f1 = new TorrentFile(60, Arrays.asList("dir".getBytes(), "f1.txt".getBytes()));
        TorrentFile f2 = new TorrentFile(80, Arrays.asList("dir".getBytes(), "f2.txt".getBytes()));
        
        TorrentMetadata metadata = new TorrentMetadata("http://tracker", null, "multi", "multi".getBytes(),
            100, new byte[40], Arrays.asList(f1, f2), false, new byte[20]);
            
        try (DiskWriter writer = new DiskWriter(metadata, tempDir)) {
            // Piece 0 is 100 bytes. It should write 60 bytes to f1 and 40 bytes to f2.
            byte[] data = new byte[100];
            Arrays.fill(data, 0, 60, (byte) 1); // f1
            Arrays.fill(data, 60, 100, (byte) 2); // f2
            
            writer.writePiece(0, data);
            
            Path p1 = tempDir.resolve("multi/dir/f1.txt");
            Path p2 = tempDir.resolve("multi/dir/f2.txt");
            
            assertTrue(Files.exists(p1));
            assertTrue(Files.exists(p2));
            
            assertEquals(60, Files.size(p1));
            assertEquals(40, Files.size(p2));
            
            byte[] r1 = Files.readAllBytes(p1);
            byte[] r2 = Files.readAllBytes(p2);
            
            for (byte b : r1) assertEquals((byte) 1, b);
            for (byte b : r2) assertEquals((byte) 2, b);
            
            // Piece 1 is 40 bytes. It should write 40 bytes to f2 starting at offset 40.
            byte[] data2 = new byte[40];
            Arrays.fill(data2, (byte) 3);
            
            writer.writePiece(1, data2);
            
            assertEquals(80, Files.size(p2));
            byte[] r2_full = Files.readAllBytes(p2);
            
            for (int i = 0; i < 40; i++) assertEquals((byte) 2, r2_full[i]);
            for (int i = 40; i < 80; i++) assertEquals((byte) 3, r2_full[i]);
        }
    }
}
