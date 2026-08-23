package com.torrentx.torrent;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TorrentMetadataTest {

    @Test
    void testTorrentMetadataCreationAndGetters() {
        String announce = "http://tracker.example.com/announce";
        byte[] infoHash = new byte[20];
        infoHash[0] = 1;
        infoHash[19] = 2;
        String name = "ubuntu-iso";
        long pieceLength = 262144;
        
        List<byte[]> pieces = new ArrayList<>();
        byte[] piece1 = new byte[20];
        piece1[0] = 5;
        pieces.add(piece1);

        long totalLength = 1048576;

        TorrentMetadata metadata = new TorrentMetadata(announce, infoHash, name, pieceLength, pieces, totalLength);

        assertEquals(announce, metadata.getAnnounce());
        assertArrayEquals(infoHash, metadata.getInfoHash());
        assertEquals(name, metadata.getName());
        assertArrayEquals(name.getBytes(StandardCharsets.UTF_8), metadata.getRawName());
        assertEquals(pieceLength, metadata.getPieceLength());
        List<byte[]> actualPieces = metadata.getPieces(); 
        assertEquals(pieces.size(), actualPieces.size()); 
        for (int i = 0; i < pieces.size(); i++) { 
            assertArrayEquals(pieces.get(i), actualPieces.get(i)); 
        }
        assertEquals(totalLength, metadata.getTotalLength());
    }

    @Test
    void testTorrentMetadataFullConstructorAndGetters() {
        String announce = "http://tracker.example.com/announce";
        List<List<String>> announceList = List.of(List.of("http://tracker1.com"), List.of("http://tracker2.com"));
        String name = "non-utf8-name-ÿ";
        byte[] rawName = name.getBytes(StandardCharsets.ISO_8859_1);
        long pieceLength = 16384;
        byte[] pieces = new byte[40];
        pieces[0] = 9;
        pieces[20] = 10;
        
        List<TorrentFile> files = List.of(
            new TorrentFile(500, List.of("file1.bin".getBytes(StandardCharsets.UTF_8))),
            new TorrentFile(1000, List.of("sub".getBytes(StandardCharsets.UTF_8), "file2.bin".getBytes(StandardCharsets.UTF_8)))
        );
        byte[] infoHash = new byte[20];
        infoHash[5] = 42;

        TorrentMetadata metadata = new TorrentMetadata(announce, announceList, name, rawName, pieceLength, pieces, files, false, infoHash);

        assertEquals(announce, metadata.getAnnounce());
        assertEquals(announceList, metadata.getAnnounceList());
        assertEquals(name, metadata.getName());
        assertArrayEquals(rawName, metadata.getRawName());
        assertEquals(pieceLength, metadata.getPieceLength());
        assertArrayEquals(pieces, metadata.getRawPieces());
        assertEquals(2, metadata.getPieceCount());
        
        byte[] expectedHash0 = new byte[20]; expectedHash0[0] = 9;
        assertArrayEquals(expectedHash0, metadata.getPieceHash(0));
        
        byte[] expectedHash1 = new byte[20]; expectedHash1[0] = 10;
        assertArrayEquals(expectedHash1, metadata.getPieceHash(1));

        assertEquals(files, metadata.getFiles());
        assertFalse(metadata.isSingleFile());
        assertEquals(1500L, metadata.getTotalLength());
        assertArrayEquals(infoHash, metadata.getInfoHash());
    }
}
