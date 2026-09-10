package com.torrentx.download;

import com.torrentx.torrent.TorrentMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TorrentLayoutTest {

    private TorrentMetadata createMockMetadata(long totalLength, long pieceLength) {
        int pieceCount = (int) ((totalLength + pieceLength - 1) / pieceLength);
        List<byte[]> pieces = new ArrayList<>();
        for (int i = 0; i < pieceCount; i++) {
            byte[] hash = new byte[20];
            Arrays.fill(hash, (byte) i);
            pieces.add(hash);
        }
        byte[] infoHash = new byte[20];
        return new TorrentMetadata("http://tracker", infoHash, "test", pieceLength, pieces, totalLength);
    }

    @Test
    void testPerfectlyDivisible() {
        // Total: 64KB, Piece: 32KB, Block: 16KB
        // Pieces: 2
        // Each piece: 2 blocks of 16KB
        TorrentMetadata metadata = createMockMetadata(65536, 32768);
        TorrentLayout layout = new TorrentLayout(metadata, 16384);
        
        assertEquals(2, layout.getTotalPieces());
        
        Piece p0 = layout.getPiece(0);
        assertEquals(32768, p0.getLength());
        assertEquals(2, p0.getBlocks().size());
        assertEquals(16384, p0.getBlocks().get(0).getLength());
        assertEquals(16384, p0.getBlocks().get(1).getLength());
        assertEquals(16384, p0.getBlocks().get(1).getOffset());

        Piece p1 = layout.getPiece(1);
        assertEquals(32768, p1.getLength());
        assertEquals(2, p1.getBlocks().size());
        assertEquals(16384, p1.getBlocks().get(0).getLength());
        assertEquals(16384, p1.getBlocks().get(1).getLength());
    }

    @Test
    void testNotDivisibleByPieceLength() {
        // Total: 70000, Piece: 32768, Block: 16384
        // Pieces: 3
        // Piece 0: 32768
        // Piece 1: 32768
        // Piece 2: 70000 - 65536 = 4464
        TorrentMetadata metadata = createMockMetadata(70000, 32768);
        TorrentLayout layout = new TorrentLayout(metadata, 16384);
        
        assertEquals(3, layout.getTotalPieces());
        
        Piece p2 = layout.getPiece(2);
        assertEquals(4464, p2.getLength());
        assertEquals(1, p2.getBlocks().size()); // Only 1 block needed for 4464 bytes
        
        Block b0 = p2.getBlocks().get(0);
        assertEquals(0, b0.getOffset());
        assertEquals(4464, b0.getLength());
    }

    @Test
    void testPieceNotDivisibleByBlockSize() {
        // Total: 32768, Piece: 32768, Block: 10000
        // Piece 0: 32768
        // Blocks: 10000, 10000, 10000, 2768
        TorrentMetadata metadata = createMockMetadata(32768, 32768);
        TorrentLayout layout = new TorrentLayout(metadata, 10000);
        
        assertEquals(1, layout.getTotalPieces());
        Piece p0 = layout.getPiece(0);
        List<Block> blocks = p0.getBlocks();
        
        assertEquals(4, blocks.size());
        
        assertEquals(10000, blocks.get(0).getLength());
        assertEquals(0, blocks.get(0).getOffset());
        
        assertEquals(10000, blocks.get(1).getLength());
        assertEquals(10000, blocks.get(1).getOffset());
        
        assertEquals(10000, blocks.get(2).getLength());
        assertEquals(20000, blocks.get(2).getOffset());
        
        assertEquals(2768, blocks.get(3).getLength());
        assertEquals(30000, blocks.get(3).getOffset());
    }

    @Test
    void testTorrentSmallerThanOnePiece() {
        // Total: 500, Piece: 32768, Block: 16384
        TorrentMetadata metadata = createMockMetadata(500, 32768);
        TorrentLayout layout = new TorrentLayout(metadata, 16384);
        
        assertEquals(1, layout.getTotalPieces());
        Piece p0 = layout.getPiece(0);
        
        assertEquals(500, p0.getLength());
        assertEquals(1, p0.getBlocks().size());
        assertEquals(500, p0.getBlocks().get(0).getLength());
    }

    @Test
    void testInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new TorrentLayout(null, 16384));
        
        TorrentMetadata metadata = createMockMetadata(500, 32768);
        assertThrows(IllegalArgumentException.class, () -> new TorrentLayout(metadata, 0));
        assertThrows(IllegalArgumentException.class, () -> new TorrentLayout(metadata, -1));
    }
}
