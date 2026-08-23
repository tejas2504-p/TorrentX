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

    @Test
    void testOnePiece() {
        byte[] pieces = new byte[20];
        pieces[0] = 7;
        pieces[19] = 8;
        
        TorrentMetadata metadata = new TorrentMetadata(
            "http://tracker.com",
            null,
            "test",
            "test".getBytes(StandardCharsets.UTF_8),
            16384,
            pieces,
            Collections.emptyList(),
            true,
            new byte[20]
        );
        
        assertEquals(1, metadata.getPieceCount());
        assertArrayEquals(pieces, metadata.getRawPieces());
        assertEquals((byte) 7, metadata.getPieceHash(0)[0]);
        assertEquals((byte) 8, metadata.getPieceHash(0)[19]);
    }

    @Test
    void testMultiplePieces() {
        byte[] pieces = new byte[60]; // 3 pieces
        pieces[0] = 1;
        pieces[20] = 2;
        pieces[40] = 3;
        
        TorrentMetadata metadata = new TorrentMetadata(
            "http://tracker.com",
            null,
            "test",
            "test".getBytes(StandardCharsets.UTF_8),
            16384,
            pieces,
            Collections.emptyList(),
            true,
            new byte[20]
        );
        
        assertEquals(3, metadata.getPieceCount());
        assertEquals((byte) 1, metadata.getPieceHash(0)[0]);
        assertEquals((byte) 2, metadata.getPieceHash(1)[0]);
        assertEquals((byte) 3, metadata.getPieceHash(2)[0]);
        
        List<byte[]> list = metadata.getPieces();
        assertEquals(3, list.size());
        assertArrayEquals(metadata.getPieceHash(0), list.get(0));
        assertArrayEquals(metadata.getPieceHash(1), list.get(1));
        assertArrayEquals(metadata.getPieceHash(2), list.get(2));
    }

    @Test
    void testInvalidPiecesLength() {
        // pieces length is 19 (not divisible by 20)
        byte[] invalidPieces = new byte[19];
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com",
            null,
            "test",
            "test".getBytes(StandardCharsets.UTF_8),
            16384,
            invalidPieces,
            Collections.emptyList(),
            true,
            new byte[20]
        ));

        // null pieces list compatibility constructor test
        List<byte[]> invalidPiecesList = new ArrayList<>();
        invalidPiecesList.add(new byte[19]); // Entry not 20 bytes
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com",
            new byte[20],
            "test",
            16384,
            invalidPiecesList,
            1000
        ));
    }

    @Test
    void testEmptyPieces() {
        byte[] emptyPieces = new byte[0];
        TorrentMetadata metadata = new TorrentMetadata(
            "http://tracker.com",
            null,
            "test",
            "test".getBytes(StandardCharsets.UTF_8),
            16384,
            emptyPieces,
            Collections.emptyList(),
            true,
            new byte[20]
        );
        assertEquals(0, metadata.getPieceCount());
        assertEquals(0, metadata.getRawPieces().length);
    }

    @Test
    void testDefensiveCopying() {
        String announce = "http://tracker.example.com/announce";
        byte[] infoHash = new byte[20];
        infoHash[0] = 1;
        String name = "ubuntu-iso";
        long pieceLength = 262144;
        
        byte[] pieces = new byte[40];
        pieces[0] = 5;
        pieces[20] = 6;

        TorrentMetadata metadata = new TorrentMetadata(
            announce,
            null,
            name,
            name.getBytes(StandardCharsets.UTF_8),
            pieceLength,
            pieces,
            Collections.emptyList(),
            true,
            infoHash
        );

        // Modify input array, assert metadata state does not change
        pieces[0] = 99;
        infoHash[0] = 99;
        assertEquals((byte) 5, metadata.getPieceHash(0)[0]);
        assertEquals((byte) 1, metadata.getInfoHash()[0]);

        // Modify output array retrieved from getter, assert metadata state does not change
        byte[] retrievedHash = metadata.getPieceHash(0);
        retrievedHash[0] = 99;
        assertEquals((byte) 5, metadata.getPieceHash(0)[0]);

        byte[] retrievedRawPieces = metadata.getRawPieces();
        retrievedRawPieces[0] = 99;
        assertEquals((byte) 5, metadata.getPieceHash(0)[0]);
    }
}
