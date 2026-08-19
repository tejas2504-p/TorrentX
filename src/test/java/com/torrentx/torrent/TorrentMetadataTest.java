package com.torrentx.torrent;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
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
        assertEquals(pieceLength, metadata.getPieceLength());
        assertEquals(pieces, metadata.getPieces());
        assertEquals(totalLength, metadata.getTotalLength());
    }
}
