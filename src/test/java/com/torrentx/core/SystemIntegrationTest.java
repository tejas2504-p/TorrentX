package com.torrentx.core;

import com.torrentx.download.*;
import com.torrentx.peer.PeerManager;
import com.torrentx.torrent.TorrentMetadata;
import com.torrentx.tracker.PeerInfo;
import com.torrentx.upload.UploadManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemIntegrationTest {

    private Path seederDir;
    private Path leecherDir;
    
    private TorrentMetadata metadata;
    
    private PeerManager seederPeerManager;
    private DownloadManager seederDownloadManager;
    private UploadManager seederUploadManager;
    private DiskWriter seederDiskWriter;
    private PieceManager seederPieceManager;

    private PeerManager leecherPeerManager;
    private DownloadManager leecherDownloadManager;
    private UploadManager leecherUploadManager;
    private DiskWriter leecherDiskWriter;
    private PieceManager leecherPieceManager;

    @BeforeEach
    void setUp() throws Exception {
        seederDir = Files.createTempDirectory("torrentx_seeder");
        leecherDir = Files.createTempDirectory("torrentx_leecher");

        // 1. Generate 32KB of random data
        byte[] data = new byte[32768];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 255);
        }
        
        // Write data to seeder dir
        Path seederFile = seederDir.resolve("testfile.dat");
        Files.write(seederFile, data);

        // 2. Compute SHA-1 for the 2 pieces (16KB each)
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash0 = md.digest(Arrays.copyOfRange(data, 0, 16384));
        md.reset();
        byte[] hash1 = md.digest(Arrays.copyOfRange(data, 16384, 32768));
        
        List<byte[]> pieceHashes = new ArrayList<>();
        pieceHashes.add(hash0);
        pieceHashes.add(hash1);
        
        // 3. Create metadata
        byte[] infoHash = new byte[20];
        metadata = new TorrentMetadata("http://tracker", infoHash, "testfile.dat", 16384, pieceHashes, 32768);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (seederDownloadManager != null) seederDownloadManager.close();
        if (seederUploadManager != null) seederUploadManager.close();
        if (seederPeerManager != null) seederPeerManager.close();
        if (seederDiskWriter != null) seederDiskWriter.close();
        
        if (leecherDownloadManager != null) leecherDownloadManager.close();
        if (leecherUploadManager != null) leecherUploadManager.close();
        if (leecherPeerManager != null) leecherPeerManager.close();
        if (leecherDiskWriter != null) leecherDiskWriter.close();
        
        deleteDirectory(seederDir.toFile());
        deleteDirectory(leecherDir.toFile());
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    @Test
    void testEndToEndUploadAndDownload() throws Exception {
        // --- Setup Seeder ---
        TorrentLayout layout = new TorrentLayout(metadata, 16384); // 16KB blocks
        seederPieceManager = new PieceManager(layout);
        // Seeder starts with 100% completed pieces
        byte[] fullData = Files.readAllBytes(seederDir.resolve("testfile.dat"));
        seederPieceManager.markBlockRequested(0, 0, 16384);
        seederPieceManager.markBlockReceived(0, 0, Arrays.copyOfRange(fullData, 0, 16384));
        seederPieceManager.markBlockRequested(1, 0, 16384);
        seederPieceManager.markBlockReceived(1, 0, Arrays.copyOfRange(fullData, 16384, 32768));
        
        // Hack to simulate 100% seeding without going through normal flow
        java.lang.reflect.Field completedPiecesField = PieceManager.class.getDeclaredField("completedPieces");
        completedPiecesField.setAccessible(true);
        java.util.BitSet bs = (java.util.BitSet) completedPiecesField.get(seederPieceManager);
        bs.set(0);
        bs.set(1);
        
        seederDiskWriter = new DiskWriter(metadata, seederDir);
        PieceAvailability seederAvailability = new PieceAvailability(2);
        PieceAssembler seederAssembler = new PieceAssembler(seederPieceManager);
        BlockSelector seederBlockSelector = new BlockSelector(seederPieceManager, 5);
        
        seederPeerManager = new PeerManager(metadata.getInfoHash(), "SEEDER-0123456789ABC".getBytes(), 50);
        seederPeerManager.setPieceAvailability(seederAvailability);
        seederPeerManager.setBlockSelector(seederBlockSelector);
        seederPeerManager.setPieceManager(seederPieceManager);
        seederPeerManager.setDiskWriter(seederDiskWriter);
        
        seederUploadManager = new UploadManager(4);
        seederPeerManager.setUploadManager(seederUploadManager);
        
        seederDownloadManager = new DownloadManager(seederPeerManager, seederPieceManager, new RarestFirstPieceSelector(), seederBlockSelector, seederAvailability, seederAssembler, seederDiskWriter);
        
        seederPeerManager.bind(0);
        int seederPort = seederPeerManager.getBoundPort();
        
        seederPeerManager.start();
        seederUploadManager.start();
        seederDownloadManager.start();

        // --- Setup Leecher ---
        leecherPieceManager = new PieceManager(layout);
        leecherDiskWriter = new DiskWriter(metadata, leecherDir);
        PieceAvailability leecherAvailability = new PieceAvailability(2);
        PieceAssembler leecherAssembler = new PieceAssembler(leecherPieceManager);
        BlockSelector leecherBlockSelector = new BlockSelector(leecherPieceManager, 5);
        
        leecherPeerManager = new PeerManager(metadata.getInfoHash(), "LEECHER-0123456789AB".getBytes(), 50);
        leecherPeerManager.setPieceAvailability(leecherAvailability);
        leecherPeerManager.setBlockSelector(leecherBlockSelector);
        leecherPeerManager.setPieceManager(leecherPieceManager);
        leecherPeerManager.setDiskWriter(leecherDiskWriter);
        
        leecherUploadManager = new UploadManager(4);
        leecherPeerManager.setUploadManager(leecherUploadManager);
        
        leecherDownloadManager = new DownloadManager(leecherPeerManager, leecherPieceManager, new RarestFirstPieceSelector(), leecherBlockSelector, leecherAvailability, leecherAssembler, leecherDiskWriter);
        
        leecherPeerManager.bind(0);
        
        leecherPeerManager.start();
        leecherUploadManager.start();
        leecherDownloadManager.start();

        // --- Execute Transfer ---
        // Connect Leecher to Seeder
        List<PeerInfo> discoveredPeers = new ArrayList<>();
        discoveredPeers.add(new PeerInfo("127.0.0.1", seederPort, new byte[20]));
        leecherPeerManager.addPeers(discoveredPeers);

        // Wait for download to complete
        long startTime = System.currentTimeMillis();
        boolean success = false;
        
        while (System.currentTimeMillis() - startTime < 10000) { // 10s timeout
            if (leecherDownloadManager.isComplete()) {
                success = true;
                break;
            }
            // Force leecher to broadcast interest if needed (bitfield will trigger it automatically)
            Thread.sleep(100);
            
            // Note: because Seeder doesn't automatically broadcast its bitfield without logic we bypassed,
            // we will simulate the seeder's HAVE messages so leecher knows it has pieces.
            for (com.torrentx.peer.PeerConnection conn : seederPeerManager.getConnectedPeers()) {
                if (conn.getState() == com.torrentx.peer.PeerConnectionState.READY) {
                    // Send bitfield
                    byte[] bitfield = new byte[] { (byte) 0xC0 }; // 11000000 -> pieces 0, 1
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(5 + 1);
                    bb.putInt(2);
                    bb.put((byte) 5);
                    bb.put(bitfield);
                    bb.flip();
                    conn.getWriteQueue().offer(bb);
                    if (conn.getSelectionKey() != null && conn.getSelectionKey().isValid()) {
                        conn.getSelectionKey().interestOps(conn.getSelectionKey().interestOps() | java.nio.channels.SelectionKey.OP_WRITE);
                    }
                }
            }
        }

        assertTrue(success, "Download did not complete within timeout");
        
        // Verify files on disk match
        byte[] seederData = Files.readAllBytes(seederDir.resolve("testfile.dat"));
        byte[] leecherData = Files.readAllBytes(leecherDir.resolve("testfile.dat"));
        
        assertTrue(Arrays.equals(seederData, leecherData), "Downloaded data does not match original data!");
    }
}
