package com.torrentx.storage;

import com.torrentx.bencode.BencodeDecoder;
import com.torrentx.bencode.BencodeEncoder;
import com.torrentx.bencode.Metainfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class StorageManagerTest {

    @TempDir
    File tempDir;

    private Metainfo singleFileMetainfo;
    private Metainfo multiFileMetainfo;
    
    private byte[] singlePieceHash;
    private byte[] multiPieceHash1;
    private byte[] multiPieceHash2;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        // --- Setup Single File Torrent Metainfo ---
        Map<String, Object> singleInfo = new LinkedHashMap<>();
        singleInfo.put("name", "single.txt");
        singleInfo.put("piece length", 1000L);
        
        byte[] piece0Data = new byte[1000];
        Arrays.fill(piece0Data, (byte) 'a');
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        singlePieceHash = digest.digest(piece0Data);
        
        singleInfo.put("pieces", singlePieceHash);
        singleInfo.put("length", 1000L);

        Map<String, Object> singleTorrent = new LinkedHashMap<>();
        singleTorrent.put("announce", "http://tracker/announce");
        singleTorrent.put("info", singleInfo);

        byte[] rawSingleInfo = BencodeEncoder.encode(singleInfo);
        byte[] rawSingleTorrent = BencodeEncoder.encode(singleTorrent);
        BencodeDecoder singleDecoder = new BencodeDecoder(rawSingleTorrent);
        singleFileMetainfo = new Metainfo((Map<String, Object>) singleDecoder.decode(), singleDecoder.getRawInfoBytes());

        // --- Setup Multi File Torrent Metainfo ---
        Map<String, Object> multiInfo = new LinkedHashMap<>();
        multiInfo.put("name", "multi_project");
        multiInfo.put("piece length", 1000L);

        List<Map<String, Object>> filesList = new ArrayList<>();
        Map<String, Object> f1 = new LinkedHashMap<>();
        f1.put("length", 600L);
        f1.put("path", Arrays.asList("file1.dat"));
        filesList.add(f1);

        Map<String, Object> f2 = new LinkedHashMap<>();
        f2.put("length", 800L);
        f2.put("path", Arrays.asList("subdir", "file2.dat"));
        filesList.add(f2);
        
        multiInfo.put("files", filesList);

        byte[] p0Data = new byte[1000];
        Arrays.fill(p0Data, 0, 600, (byte) 'x');
        Arrays.fill(p0Data, 600, 1000, (byte) 'y');
        multiPieceHash1 = digest.digest(p0Data);

        byte[] p1Data = new byte[400];
        Arrays.fill(p1Data, (byte) 'y');
        multiPieceHash2 = digest.digest(p1Data);

        byte[] concatHashes = new byte[40];
        System.arraycopy(multiPieceHash1, 0, concatHashes, 0, 20);
        System.arraycopy(multiPieceHash2, 0, concatHashes, 20, 20);
        multiInfo.put("pieces", concatHashes);

        Map<String, Object> multiTorrent = new LinkedHashMap<>();
        multiTorrent.put("announce", "http://tracker/announce");
        multiTorrent.put("info", multiInfo);

        byte[] rawMultiInfo = BencodeEncoder.encode(multiInfo);
        byte[] rawMultiTorrent = BencodeEncoder.encode(multiTorrent);
        BencodeDecoder multiDecoder = new BencodeDecoder(rawMultiTorrent);
        multiFileMetainfo = new Metainfo((Map<String, Object>) multiDecoder.decode(), multiDecoder.getRawInfoBytes());
    }

    @Test
    public void testSingleFileAllocationAndWrite() throws IOException {
        StorageManager sm = new StorageManager(tempDir, singleFileMetainfo);
        sm.initialize();

        File expectedFile = new File(tempDir, "single.txt");
        assertTrue(expectedFile.exists());
        assertEquals(1000L, expectedFile.length());

        byte[] blockData = "hello world".getBytes(StandardCharsets.UTF_8);
        sm.writeBlock(0, 100, blockData);

        byte[] readBack = sm.readBlock(0, 100, blockData.length);
        assertArrayEquals(blockData, readBack);
    }

    @Test
    public void testPieceVerificationSuccessAndFailure() throws IOException {
        StorageManager sm = new StorageManager(tempDir, singleFileMetainfo);
        sm.initialize();

        byte[] piece0Data = new byte[1000];
        Arrays.fill(piece0Data, (byte) 'a');
        sm.writeBlock(0, 0, piece0Data);

        assertTrue(sm.verifyPiece(0));

        sm.writeBlock(0, 500, "corrupted".getBytes(StandardCharsets.UTF_8));
        assertFalse(sm.verifyPiece(0));
    }

    @Test
    public void testMultiFileBoundaryWritesAndReads() throws Exception {
        StorageManager sm = new StorageManager(tempDir, multiFileMetainfo);
        sm.initialize();

        File file1 = new File(tempDir, "multi_project/file1.dat");
        File file2 = new File(tempDir, "multi_project/subdir/file2.dat");

        assertTrue(file1.exists());
        assertTrue(file2.exists());
        assertEquals(600L, file1.length());
        assertEquals(800L, file2.length());

        byte[] part1 = new byte[600];
        Arrays.fill(part1, (byte) 'x');
        byte[] part2 = new byte[400];
        Arrays.fill(part2, (byte) 'y');

        sm.writeBlock(0, 0, part1);
        sm.writeBlock(0, 600, part2);

        sm.writeBlock(1, 0, part2);

        assertTrue(sm.verifyPiece(0));
        assertTrue(sm.verifyPiece(1));

        byte[] readBackFile1 = sm.readBlock(0, 0, 600);
        assertArrayEquals(part1, readBackFile1);

        byte[] readBackFile2Part1 = sm.readBlock(0, 600, 400);
        assertArrayEquals(part2, readBackFile2Part1);
        
        byte[] readBackFile2Part2 = sm.readBlock(1, 0, 400);
        assertArrayEquals(part2, readBackFile2Part2);
    }
}
