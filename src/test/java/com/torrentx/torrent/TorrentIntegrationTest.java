package com.torrentx.torrent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TorrentIntegrationTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpFixtures() throws Exception {
        String singleBencode = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee";
        String multiBencode = "d8:announce35:http://tracker.example.com/announce13:announce-listll18:http://backup1.comel18:http://backup2.comee4:infod5:filesld6:lengthi20000e4:pathl4:sub19:fileA.txteed6:lengthi30000e4:pathl9:fileB.txteee4:name9:multi-dir12:piece lengthi32768e6:pieces40:12345678901234567890abcdefghijklmnopqrstee";

        Path fixturesDir = Paths.get("src", "test", "resources", "fixtures");
        Files.createDirectories(fixturesDir);
        Files.write(fixturesDir.resolve("single_file.torrent"), singleBencode.getBytes(StandardCharsets.UTF_8));
        Files.write(fixturesDir.resolve("multi_file.torrent"), multiBencode.getBytes(StandardCharsets.UTF_8));
        
        Path targetFixturesDir = Paths.get("target", "test-classes", "fixtures");
        if (Files.exists(Paths.get("target", "test-classes"))) {
            Files.createDirectories(targetFixturesDir);
            Files.write(targetFixturesDir.resolve("single_file.torrent"), singleBencode.getBytes(StandardCharsets.UTF_8));
            Files.write(targetFixturesDir.resolve("multi_file.torrent"), multiBencode.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] sha1(byte[] input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return md.digest(input);
    }

    @Test
    void testIntegrationValidSingleFileTorrent() throws Exception {
        java.net.URL resourceUrl = getClass().getClassLoader().getResource("fixtures/single_file.torrent");
        assertNotNull(resourceUrl, "single_file.torrent fixture not found");
        File file = new File(resourceUrl.toURI());

        TorrentParser parser = new TorrentParser();
        TorrentMetadata metadata = parser.parse(file);

        assertEquals("http://tracker.example.com/announce", metadata.getAnnounce());
        assertEquals("single.txt", metadata.getName());
        assertEquals(16384L, metadata.getPieceLength());
        assertEquals(1, metadata.getPieceCount());
        assertTrue(metadata.isSingleFile());
    }

    @Test
    void testIntegrationValidMultiFileTorrent() throws Exception {
        java.net.URL resourceUrl = getClass().getClassLoader().getResource("fixtures/multi_file.torrent");
        assertNotNull(resourceUrl, "multi_file.torrent fixture not found");
        File file = new File(resourceUrl.toURI());

        TorrentParser parser = new TorrentParser();
        TorrentMetadata metadata = parser.parse(file);

        assertEquals("multi-dir", metadata.getName());
        assertEquals(32768L, metadata.getPieceLength());
        assertFalse(metadata.isSingleFile());
        assertEquals(2, metadata.getFiles().size());
        assertEquals(List.of("sub1", "fileA.txt"), metadata.getFiles().get(0).getPath());
    }

    @Test
    void testIntegrationMissingTorrentFile() {
        TorrentParser parser = new TorrentParser();
        Path missingPath = tempDir.resolve("missing.torrent");
        assertThrows(TorrentException.class, () -> parser.parse(missingPath.toAbsolutePath().toString()));
    }

    @Test
    void testIntegrationCorruptedTorrent() throws Exception {
        TorrentParser parser = new TorrentParser();
        Path corruptPath = tempDir.resolve("corrupt.torrent");
        Files.write(corruptPath, "d8:announce35:invalid_bencode...".getBytes(StandardCharsets.US_ASCII));
        assertThrows(TorrentException.class, () -> parser.parse(corruptPath));
    }

    @Test
    void testIntegrationMissingInfoDictionary() throws Exception {
        TorrentParser parser = new TorrentParser();
        byte[] missingInfo = "d8:announce35:http://tracker.example.com/announcee".getBytes(StandardCharsets.US_ASCII);
        Path path = tempDir.resolve("missing_info.torrent");
        Files.write(path, missingInfo);
        assertThrows(TorrentException.class, () -> parser.parse(path));
    }

    @Test
    void testIntegrationInvalidPieceHashes() throws Exception {
        TorrentParser parser = new TorrentParser();
        byte[] invalidPieces = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces40:12345678901234567890abcdefghijklmnopqrstee".getBytes(StandardCharsets.US_ASCII);
        Path path = tempDir.resolve("invalid_pieces.torrent");
        Files.write(path, invalidPieces);
        assertThrows(TorrentException.class, () -> parser.parse(path));
    }

    @Test
    void testIntegrationInvalidFileMetadata() throws Exception {
        TorrentParser parser = new TorrentParser();
        byte[] invalidFile = "d8:announce35:http://tracker.example.com/announce4:infod5:filesld6:lengthi100eee4:name9:multi-dir12:piece lengthi32768e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        Path path = tempDir.resolve("invalid_file.torrent");
        Files.write(path, invalidFile);
        assertThrows(TorrentException.class, () -> parser.parse(path));
    }

    @Test
    void testIntegrationMissingAnnounceMetadata() throws Exception {
        TorrentParser parser = new TorrentParser();
        byte[] missingAnnounce = "d4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee".getBytes(StandardCharsets.US_ASCII);
        Path path = tempDir.resolve("missing_announce.torrent");
        Files.write(path, missingAnnounce);
        TorrentMetadata metadata = parser.parse(path);
        assertNull(metadata.getAnnounce());
    }

    @Test
    void testIntegrationCorrectInfoHash() throws Exception {
        java.net.URL resourceUrl = getClass().getClassLoader().getResource("fixtures/single_file.torrent");
        assertNotNull(resourceUrl, "single_file.torrent fixture not found");
        File file = new File(resourceUrl.toURI());

        TorrentParser parser = new TorrentParser();
        TorrentMetadata metadata = parser.parse(file);

        byte[] infoDictPrefix = "d6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:".getBytes(StandardCharsets.US_ASCII);
        byte[] infoDictSuffix = "e".getBytes(StandardCharsets.US_ASCII);
        byte[] expectedInfoDictBytes = new byte[infoDictPrefix.length + 20 + infoDictSuffix.length];
        System.arraycopy(infoDictPrefix, 0, expectedInfoDictBytes, 0, infoDictPrefix.length);
        System.arraycopy("12345678901234567890".getBytes(StandardCharsets.US_ASCII), 0, expectedInfoDictBytes, infoDictPrefix.length, 20);
        System.arraycopy(infoDictSuffix, 0, expectedInfoDictBytes, infoDictPrefix.length + 20, infoDictSuffix.length);

        assertArrayEquals(sha1(expectedInfoDictBytes), metadata.getInfoHash());
    }

    @Test
    void testIntegrationCorrectTotalTorrentSize() throws Exception {
        java.net.URL resourceUrl = getClass().getClassLoader().getResource("fixtures/multi_file.torrent");
        assertNotNull(resourceUrl, "multi_file.torrent fixture not found");
        File file = new File(resourceUrl.toURI());

        TorrentParser parser = new TorrentParser();
        TorrentMetadata metadata = parser.parse(file);

        assertEquals(50000L, metadata.getTotalLength());
    }
}
