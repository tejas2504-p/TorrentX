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

        long totalLength = 262144; // match pieceLength for expected pieces count = 1

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
            new TorrentFile(15000, List.of("file1.bin".getBytes(StandardCharsets.UTF_8))),
            new TorrentFile(17000, List.of("sub".getBytes(StandardCharsets.UTF_8), "file2.bin".getBytes(StandardCharsets.UTF_8)))
        ); // Total length = 32000. Piece length = 16384. Expected pieces count = (32000 + 16384 - 1)/16384 = 2.
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
        assertEquals(32000L, metadata.getTotalLength());
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
            List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))),
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
            List.of(new TorrentFile(40000, List.of("file".getBytes(StandardCharsets.UTF_8)))), // total = 40000. Expected pieces = (40000 + 16384 - 1)/16384 = 3
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
        byte[] invalidPieces = new byte[19];
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com",
            null,
            "test",
            "test".getBytes(StandardCharsets.UTF_8),
            16384,
            invalidPieces,
            List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))),
            true,
            new byte[20]
        ));
    }

    @Test
    void testEmptyPieces() {
        // pieceLength is positive, empty pieces requires totalLength to be 0... but totalLength must be positive (> 0)!
        // Therefore, we cannot construct a valid TorrentMetadata with empty pieces because it violates piece count consistency:
        // totalLength > 0 -> expected pieces >= 1, but empty pieces -> actual pieces count = 0.
        // Thus, constructing with empty pieces is correctly rejected due to consistency!
        byte[] emptyPieces = new byte[0];
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com",
            null,
            "test",
            "test".getBytes(StandardCharsets.UTF_8),
            16384,
            emptyPieces,
            List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))),
            true,
            new byte[20]
        ));
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
            List.of(new TorrentFile(262145, List.of("file".getBytes(StandardCharsets.UTF_8)))), // expected pieces count = 2
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

    @Test
    void testConstructorValidationChecks() {
        // Announce URL validation
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "   ", // empty announce URL
            null, "test", "test".getBytes(StandardCharsets.UTF_8), 16384, new byte[20],
            List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))), true, new byte[20]
        ));

        // Tracker list validation
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com",
            List.of(Collections.singletonList("  ")), // blank URL inside announce list
            "test", "test".getBytes(StandardCharsets.UTF_8), 16384, new byte[20],
            List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))), true, new byte[20]
        ));

        // Name blank checks
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com", null,
            "  ", // blank name
            "test".getBytes(StandardCharsets.UTF_8), 16384, new byte[20],
            List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))), true, new byte[20]
        ));

        // Name directory traversal
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com", null,
            "..", // dot dot
            "test".getBytes(StandardCharsets.UTF_8), 16384, new byte[20],
            List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))), true, new byte[20]
        ));

        // Zero or negative piece length
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com", null, "test", "test".getBytes(StandardCharsets.UTF_8),
            0, // zero piece length
            new byte[20], List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))), true, new byte[20]
        ));

        // Invalid info hash length
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com", null, "test", "test".getBytes(StandardCharsets.UTF_8), 16384, new byte[20],
            List.of(new TorrentFile(1000, List.of("file".getBytes(StandardCharsets.UTF_8)))), true,
            new byte[19] // invalid info-hash
        ));

        // Invalid files list
        assertThrows(IllegalArgumentException.class, () -> new TorrentMetadata(
            "http://tracker.com", null, "test", "test".getBytes(StandardCharsets.UTF_8), 16384, new byte[20],
            Collections.emptyList(), true, new byte[20]
        ));

        // Traversal segment in TorrentFile path
        assertThrows(IllegalArgumentException.class, () -> new TorrentFile(1000, List.of("..".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> new TorrentFile(1000, List.of(".".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> new TorrentFile(1000, List.of("".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IllegalArgumentException.class, () -> new TorrentFile(1000, List.of(new byte[0])));
    }
}
