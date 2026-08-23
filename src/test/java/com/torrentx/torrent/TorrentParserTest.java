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
        byte[] dummyHash = new byte[20];
        dummyHash[0] = (byte) 0xAA;
        dummyHash[19] = (byte) 0xBB;
        
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
        // Multi-file total length must be consistent with piece length & actual piece hashes count.
        // We set length of file A = 20000, file B = 30000. Total = 50000.
        // Piece length = 32768. Expected pieces = (50000 + 32768 - 1) / 32768 = 2.
        // We supply 40 bytes of dummy piece hashes (exactly 2 pieces).
        byte[] dummyHashes = new byte[40];
        dummyHashes[0] = 1;
        dummyHashes[20] = 2;
        
        byte[] prefix = "d8:announce35:http://tracker.example.com/announce13:announce-listll18:http://backup1.comel18:http://backup2.comee4:infod5:filesld6:lengthi20000e4:pathl4:sub19:fileA.txteed6:lengthi30000e4:pathl9:fileB.txteee4:name9:multi-dir12:piece lengthi32768e6:pieces40:".getBytes(StandardCharsets.US_ASCII);
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
        assertEquals(50000L, metadata.getTotalLength());
        
        List<TorrentFile> files = metadata.getFiles();
        assertEquals(2, files.size());
        
        TorrentFile file1 = files.get(0);
        assertEquals(20000L, file1.getLength());
        assertEquals(List.of("sub1", "fileA.txt"), file1.getPath());
        assertArrayEquals("sub1".getBytes(StandardCharsets.UTF_8), file1.getRawPath().get(0));
        
        TorrentFile file2 = files.get(1);
        assertEquals(30000L, file2.getLength());
        assertEquals(List.of("fileB.txt"), file2.getPath());
    }

    @Test
    void testValidationConstraints() {
        TorrentParser parser = new TorrentParser();
        
        assertThrows(TorrentException.class, () -> parser.parse("i42e".getBytes(StandardCharsets.US_ASCII)));
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

        TorrentMetadata metadataStr = parser.parse(torrentPath.toAbsolutePath().toString());
        assertNotNull(metadataStr);
        assertEquals("http://tracker.example.com/announce", metadataStr.getAnnounce());

        TorrentMetadata metadataFile = parser.parse(torrentPath.toFile());
        assertNotNull(metadataFile);

        TorrentMetadata metadataPath = parser.parse(torrentPath);
        assertNotNull(metadataPath);
    }

    @Test
    void testParseMissingFile() {
        TorrentParser parser = new TorrentParser();
        Path missingPath = tempDir.resolve("non_existent.torrent");
        assertThrows(TorrentException.class, () -> parser.parse(missingPath.toAbsolutePath().toString()));
    }

    @Test
    void testParseEmptyFile() throws Exception {
        TorrentParser parser = new TorrentParser();
        Path emptyPath = tempDir.resolve("empty.torrent");
        Files.write(emptyPath, new byte[0]);
        assertThrows(TorrentException.class, () -> parser.parse(emptyPath));
    }

    @Test
    void testParseInvalidBencodeFile() throws Exception {
        TorrentParser parser = new TorrentParser();
        Path invalidPath = tempDir.resolve("invalid.torrent");
        Files.write(invalidPath, "d8:announce35:invalid_bencode...".getBytes(StandardCharsets.US_ASCII));
        assertThrows(TorrentException.class, () -> parser.parse(invalidPath));
    }

    @Test
    void testParseRootNotDictionaryFile() throws Exception {
        TorrentParser parser = new TorrentParser();
        Path listPath = tempDir.resolve("list.torrent");
        Files.write(listPath, "le".getBytes(StandardCharsets.US_ASCII));
        assertThrows(TorrentException.class, () -> parser.parse(listPath));
    }

    @Test
    void testMetadataRequiredFieldsValidation() {
        TorrentParser parser = new TorrentParser();

        byte[] missingAnnounce = "d4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(missingAnnounce));

        byte[] invalidAnnounceType = "d8:announcei42e4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(invalidAnnounceType));

        byte[] invalidAnnounceListType = "d8:announce35:http://tracker.example.com/announce13:announce-listd3:foo3:bare4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(invalidAnnounceListType));

        byte[] missingPieceLength = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(missingPieceLength));

        byte[] emptyFilesList = "d8:announce35:http://tracker.example.com/announce4:infod5:filesle4:name9:multi-dir12:piece lengthi32768e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(emptyFilesList));

        byte[] emptyPathList = "d8:announce35:http://tracker.example.com/announce4:infod5:filesld6:lengthi100e4:pathleee4:name9:multi-dir12:piece lengthi32768e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(emptyPathList));
    }

    @Test
    void testNonUtf8NamePreservation() throws TorrentException {
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
        assertArrayEquals(rawNameBytes, metadata.getRawName());
    }

    @Test
    void testSingleFileZeroOrNegativeLength() {
        TorrentParser parser = new TorrentParser();

        byte[] zeroLength = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi0e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(zeroLength));

        byte[] negativeLength = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi-500e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(negativeLength));
    }

    @Test
    void testSingleFileInconsistentPieceCount() {
        TorrentParser parser = new TorrentParser();

        byte[] inconsistentPieces = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi25000e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        assertThrows(TorrentException.class, () -> parser.parse(inconsistentPieces));
    }
}
