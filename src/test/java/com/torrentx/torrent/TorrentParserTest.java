package com.torrentx.torrent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TorrentParserTest {

    @TempDir
    Path tempDir;

    private byte[] sha1(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testParseSingleFileTorrent() throws TorrentException {
        // Construct Bencode for a single-file torrent:
        // {
        //   "announce": "http://tracker.example.com/announce",
        //   "info": {
        //     "name": "single.txt",
        //     "piece length": 16384,
        //     "pieces": <20 bytes of dummy piece hash>,
        //     "length": 12345
        //   }
        // }
        byte[] dummyHash = new byte[20];
        dummyHash[0] = (byte) 0xAA;
        dummyHash[19] = (byte) 0xBB;
        
        // Let's build the Bencoded string manually:
        // d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:<hash>ee
        byte[] bencodedPrefix = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:".getBytes(StandardCharsets.US_ASCII);
        byte[] bencodedSuffix = "ee".getBytes(StandardCharsets.US_ASCII);
        
        byte[] input = new byte[bencodedPrefix.length + 20 + bencodedSuffix.length];
        System.arraycopy(bencodedPrefix, 0, input, 0, bencodedPrefix.length);
        System.arraycopy(dummyHash, 0, input, bencodedPrefix.length, 20);
        System.arraycopy(bencodedSuffix, 0, input, bencodedPrefix.length + 20, bencodedSuffix.length);
        
        TorrentParser parser = new TorrentParser();
        TorrentMetadata metadata = parser.parse(input);
        
        assertEquals("http://tracker.example.com/announce", metadata.getAnnounce());
        assertTrue(metadata.getAnnounceList().isEmpty());
        assertEquals("single.txt", metadata.getName());
        assertEquals(16384L, metadata.getPieceLength());
        assertEquals(1, metadata.getPieceCount());
        assertArrayEquals(dummyHash, metadata.getPieceHash(0));
        assertTrue(metadata.isSingleFile());
        assertEquals(12345L, metadata.getTotalLength());
        
        assertEquals(1, metadata.getFiles().size());
        TorrentFile file = metadata.getFiles().get(0);
        assertEquals(12345L, file.getLength());
        assertEquals(Collections.singletonList("single.txt"), file.getPath());
        
        // Verify info hash corresponds to SHA-1 of the exact Bencoded info dictionary
        // Bencoded info dict: d6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:<hash>ee
        byte[] infoDictPrefix = "d6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:".getBytes(StandardCharsets.US_ASCII);
        byte[] infoDictSuffix = "e".getBytes(StandardCharsets.US_ASCII);
        byte[] expectedInfoDictBytes = new byte[infoDictPrefix.length + 20 + infoDictSuffix.length];
        System.arraycopy(infoDictPrefix, 0, expectedInfoDictBytes, 0, infoDictPrefix.length);
        System.arraycopy(dummyHash, 0, expectedInfoDictBytes, infoDictPrefix.length, 20);
        System.arraycopy(infoDictSuffix, 0, expectedInfoDictBytes, infoDictPrefix.length + 20, infoDictSuffix.length);
        
        assertArrayEquals(sha1(expectedInfoDictBytes), metadata.getInfoHash());
    }

    @Test
    void testParseMultiFileTorrent() throws TorrentException {
        // Construct Bencode for a multi-file torrent:
        // {
        //   "announce": "http://tracker.example.com/announce",
        //   "announce-list": [["http://backup1.com"], ["http://backup2.com"]],
        //   "info": {
        //     "name": "multi-dir",
        //     "piece length": 32768,
        //     "pieces": <40 bytes of dummy piece hashes (2 pieces)>,
        //     "files": [
        //       { "length": 100, "path": ["sub1", "fileA.txt"] },
        //       { "length": 200, "path": ["fileB.txt"] }
        //     ]
        //   }
        // }
        byte[] dummyHashes = new byte[40];
        dummyHashes[0] = 1;
        dummyHashes[20] = 2;
        
        byte[] prefix = "d8:announce35:http://tracker.example.com/announce13:announce-listll18:http://backup1.comel18:http://backup2.comee4:infod5:filesld6:lengthi100e4:pathl4:sub19:fileA.txteed6:lengthi200e4:pathl9:fileB.txteee4:name9:multi-dir12:piece lengthi32768e6:pieces40:".getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = "ee".getBytes(StandardCharsets.US_ASCII);
        
        byte[] input = new byte[prefix.length + 40 + suffix.length];
        System.arraycopy(prefix, 0, input, 0, prefix.length);
        System.arraycopy(dummyHashes, 0, input, prefix.length, 40);
        System.arraycopy(suffix, 0, input, prefix.length + 40, suffix.length);
        
        TorrentParser parser = new TorrentParser();
        TorrentMetadata metadata = parser.parse(input);
        
        assertEquals("http://tracker.example.com/announce", metadata.getAnnounce());
        assertEquals(2, metadata.getAnnounceList().size());
        assertEquals("http://backup1.com", metadata.getAnnounceList().get(0).get(0));
        assertEquals("http://backup2.com", metadata.getAnnounceList().get(1).get(0));
        
        assertEquals("multi-dir", metadata.getName());
        assertEquals(32768L, metadata.getPieceLength());
        assertEquals(2, metadata.getPieceCount());
        byte[] expectedHash0 = new byte[20]; expectedHash0[0] = 1; assertArrayEquals(expectedHash0, metadata.getPieceHash(0)); byte[] expectedHash1 = new byte[20]; expectedHash1[0] = 2; assertArrayEquals(expectedHash1, metadata.getPieceHash(1));
        
        assertFalse(metadata.isSingleFile());
        assertEquals(300L, metadata.getTotalLength());
        
        List<TorrentFile> files = metadata.getFiles();
        assertEquals(2, files.size());
        
        TorrentFile file1 = files.get(0);
        assertEquals(100L, file1.getLength());
        assertEquals(List.of("sub1", "fileA.txt"), file1.getPath());
        assertArrayEquals("sub1".getBytes(StandardCharsets.UTF_8), file1.getRawPath().get(0));
        
        TorrentFile file2 = files.get(1);
        assertEquals(200L, file2.getLength());
        assertEquals(List.of("fileB.txt"), file2.getPath());
    }

    @Test
    void testValidationConstraints() {
        TorrentParser parser = new TorrentParser();
        
        // Root not dictionary
        assertThrows(TorrentException.class, () -> parser.parse("i42e".getBytes(StandardCharsets.US_ASCII)));
        
        // Missing info dict
        assertThrows(TorrentException.class, () -> parser.parse("d8:announce35:http://tracker.example.com/announcee".getBytes(StandardCharsets.US_ASCII)));
        
        // Invalid piece length (negative)
        byte[] invalidPieceLen = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi-16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(invalidPieceLen));
        
        // Pieces length not multiple of 20
        byte[] invalidPieces = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces19:1234567890123456789ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(invalidPieces));
        
        // Missing pieces field entirely
        byte[] missingPieces = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(missingPieces));
        
        // Single-file missing length field
        byte[] missingLen = "d8:announce35:http://tracker.example.com/announce4:infod4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890eee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(missingLen));
        
        // Multi-file missing length field in a files list entry
        byte[] missingFileLen = "d8:announce35:http://tracker.example.com/announce4:infod5:filesld4:pathl9:fileB.txteee4:name9:multi-dir12:piece lengthi32768e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(missingFileLen));
    }

    @Test
    void testParseValidTorrentFile() throws Exception {
        byte[] dummyHash = new byte[20];
        dummyHash[0] = (byte) 0xAA;
        dummyHash[19] = (byte) 0xBB;
        
        byte[] bencodedPrefix = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:".getBytes(StandardCharsets.US_ASCII);
        byte[] bencodedSuffix = "ee".getBytes(StandardCharsets.US_ASCII);
        
        byte[] input = new byte[bencodedPrefix.length + 20 + bencodedSuffix.length];
        System.arraycopy(bencodedPrefix, 0, input, 0, bencodedPrefix.length);
        System.arraycopy(dummyHash, 0, input, bencodedPrefix.length, 20);
        System.arraycopy(bencodedSuffix, 0, input, bencodedPrefix.length + 20, bencodedSuffix.length);

        Path torrentPath = tempDir.resolve("test.torrent");
        Files.write(torrentPath, input);

        TorrentParser parser = new TorrentParser();

        // 1. Test String path overload
        TorrentMetadata metadataStr = parser.parse(torrentPath.toAbsolutePath().toString());
        assertNotNull(metadataStr);
        assertEquals("http://tracker.example.com/announce", metadataStr.getAnnounce());
        assertEquals("single.txt", metadataStr.getName());

        // 2. Test File overload
        TorrentMetadata metadataFile = parser.parse(torrentPath.toFile());
        assertNotNull(metadataFile);
        assertEquals("http://tracker.example.com/announce", metadataFile.getAnnounce());

        // 3. Test Path overload
        TorrentMetadata metadataPath = parser.parse(torrentPath);
        assertNotNull(metadataPath);
        assertEquals("http://tracker.example.com/announce", metadataPath.getAnnounce());
    }

    @Test
    void testParseMissingFile() {
        TorrentParser parser = new TorrentParser();
        Path missingPath = tempDir.resolve("non_existent.torrent");
        
        // Assert throws TorrentException for missing file String path
        assertThrows(TorrentException.class, () -> parser.parse(missingPath.toAbsolutePath().toString()));
        
        // Assert throws TorrentException for missing File
        assertThrows(TorrentException.class, () -> parser.parse(missingPath.toFile()));
        
        // Assert throws TorrentException for missing Path
        assertThrows(TorrentException.class, () -> parser.parse(missingPath));
    }

    @Test
    void testParseEmptyFile() throws Exception {
        TorrentParser parser = new TorrentParser();
        Path emptyPath = tempDir.resolve("empty.torrent");
        Files.write(emptyPath, new byte[0]); // Create an empty file
        
        assertThrows(TorrentException.class, () -> parser.parse(emptyPath.toAbsolutePath().toString()));
        assertThrows(TorrentException.class, () -> parser.parse(emptyPath.toFile()));
        assertThrows(TorrentException.class, () -> parser.parse(emptyPath));
    }

    @Test
    void testParseInvalidBencodeFile() throws Exception {
        TorrentParser parser = new TorrentParser();
        Path invalidPath = tempDir.resolve("invalid.torrent");
        Files.write(invalidPath, "d8:announce35:invalid_bencode...".getBytes(StandardCharsets.US_ASCII));
        
        assertThrows(TorrentException.class, () -> parser.parse(invalidPath.toAbsolutePath().toString()));
        assertThrows(TorrentException.class, () -> parser.parse(invalidPath.toFile()));
        assertThrows(TorrentException.class, () -> parser.parse(invalidPath));
    }

    @Test
    void testParseRootNotDictionaryFile() throws Exception {
        TorrentParser parser = new TorrentParser();
        Path listPath = tempDir.resolve("list.torrent");
        Files.write(listPath, "le".getBytes(StandardCharsets.US_ASCII)); // Bencoded empty list
        
        assertThrows(TorrentException.class, () -> parser.parse(listPath.toAbsolutePath().toString()));
        assertThrows(TorrentException.class, () -> parser.parse(listPath.toFile()));
        assertThrows(TorrentException.class, () -> parser.parse(listPath));

        Path intPath = tempDir.resolve("int.torrent");
        Files.write(intPath, "i42e".getBytes(StandardCharsets.US_ASCII)); // Bencoded integer
        
        assertThrows(TorrentException.class, () -> parser.parse(intPath));
    }

    @Test
    void testMetadataRequiredFieldsValidation() {
        TorrentParser parser = new TorrentParser();

        // 1. Missing announce
        byte[] missingAnnounce = "d4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        TorrentException ex1 = assertThrows(TorrentException.class, () -> parser.parse(missingAnnounce));
        assertTrue(ex1.getMessage().contains("Missing required 'announce' field"));

        // 2. Invalid announce type (integer instead of string)
        byte[] invalidAnnounceType = "d8:announcei42e4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        TorrentException ex2 = assertThrows(TorrentException.class, () -> parser.parse(invalidAnnounceType));
        assertTrue(ex2.getMessage().contains("Invalid 'announce' field"));

        // 3. Invalid announce-list type (dictionary instead of list)
        byte[] invalidAnnounceListType = "d8:announce35:http://tracker.example.com/announce13:announce-listd3:foo3:bare4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        TorrentException ex3 = assertThrows(TorrentException.class, () -> parser.parse(invalidAnnounceListType));
        assertTrue(ex3.getMessage().contains("announce-list field must be a Bencode list"));

        // 4. Missing piece length
        byte[] missingPieceLength = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        TorrentException ex4 = assertThrows(TorrentException.class, () -> parser.parse(missingPieceLength));
        assertTrue(ex4.getMessage().contains("Missing required 'piece length' field in info"));

        // 5. Empty files list in multi-file torrent
        byte[] emptyFilesList = "d8:announce35:http://tracker.example.com/announce4:infod5:filesle4:name9:multi-dir12:piece lengthi32768e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        TorrentException ex5 = assertThrows(TorrentException.class, () -> parser.parse(emptyFilesList));
        assertTrue(ex5.getMessage().contains("files list cannot be empty in multi-file torrent"));

        // 6. Missing path segments in files list entry
        byte[] emptyPathList = "d8:announce35:http://tracker.example.com/announce4:infod5:filesld6:lengthi100e4:pathleee4:name9:multi-dir12:piece lengthi32768e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        TorrentException ex6 = assertThrows(TorrentException.class, () -> parser.parse(emptyPathList));
        assertTrue(ex6.getMessage().contains("path segments cannot be empty"));
    }

    @Test
    void testNonUtf8NamePreservation() throws TorrentException {
        // Construct Bencode where 'name' contains arbitrary non-UTF8 binary data (like 0xFF, 0x80)
        byte[] rawNameBytes = new byte[] { (byte) 't', (byte) 'e', (byte) 's', (byte) 't', (byte) 0xFF, (byte) 0x80 };
        
        byte[] dummyHash = new byte[20];
        
        byte[] bencodedAnnounce = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name6:".getBytes(StandardCharsets.US_ASCII);
        byte[] bencodedMiddle = "12:piece lengthi16384e6:pieces20:".getBytes(StandardCharsets.US_ASCII);
        byte[] bencodedSuffix = "ee".getBytes(StandardCharsets.US_ASCII);
        
        byte[] input = new byte[bencodedAnnounce.length + rawNameBytes.length + bencodedMiddle.length + 20 + bencodedSuffix.length];
        int pos = 0;
        System.arraycopy(bencodedAnnounce, 0, input, pos, bencodedAnnounce.length); pos += bencodedAnnounce.length;
        System.arraycopy(rawNameBytes, 0, input, pos, rawNameBytes.length); pos += rawNameBytes.length;
        System.arraycopy(bencodedMiddle, 0, input, pos, bencodedMiddle.length); pos += bencodedMiddle.length;
        System.arraycopy(dummyHash, 0, input, pos, 20); pos += 20;
        System.arraycopy(bencodedSuffix, 0, input, pos, bencodedSuffix.length);
        
        TorrentParser parser = new TorrentParser();
        TorrentMetadata metadata = parser.parse(input);
        
        // Assert raw name matches exactly what was input
        assertArrayEquals(rawNameBytes, metadata.getRawName());
    }
}
