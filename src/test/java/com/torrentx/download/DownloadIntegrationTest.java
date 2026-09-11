package com.torrentx.download;

import com.torrentx.peer.MockPeerServer;
import com.torrentx.peer.PeerManager;
import com.torrentx.torrent.TorrentMetadata;
import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DownloadIntegrationTest {
    private byte[] localInfoHash;
    private byte[] localPeerId;
    
    private PeerManager peerManager;
    private PieceManager pieceManager;
    private PieceSelector pieceSelector;
    private BlockSelector blockSelector;
    private PieceAvailability pieceAvailability;
    private PieceAssembler pieceAssembler;
    private DownloadManager downloadManager;

    @BeforeEach
    void setUp() throws Exception {
        localInfoHash = new byte[20];
        Arrays.fill(localInfoHash, (byte) 7);
        
        localPeerId = new byte[20];
        Arrays.fill(localPeerId, (byte) 8);
        
        peerManager = new PeerManager(localInfoHash, localPeerId, 50);
        
        // Setup Torrent Layout
        TorrentMetadata metadata = new TorrentMetadata("http://localhost:6969/announce", 
            localInfoHash, "test.txt", 16384, Collections.singletonList(new byte[20]), 16384);
        TorrentLayout layout = new TorrentLayout(metadata, 16384); 
        pieceManager = new PieceManager(layout);
        
        pieceSelector = new RarestFirstPieceSelector();
        blockSelector = new BlockSelector(pieceManager, 5);
        pieceAvailability = new PieceAvailability(layout.getTotalPieces());
        pieceAssembler = new PieceAssembler(pieceManager); // Will always fail verification since hash is fake, but we just want to see it run
        
        peerManager.setPieceAvailability(pieceAvailability);
        peerManager.setBlockSelector(blockSelector);
        
        downloadManager = new DownloadManager(peerManager, pieceManager, pieceSelector, 
                blockSelector, pieceAvailability, pieceAssembler);
        
        peerManager.start();
        downloadManager.start();
    }

    @AfterEach
    void tearDown() {
        downloadManager.close();
        peerManager.close();
    }

    @Test
    void testFullDownloadFlow() throws Exception {
        CountDownLatch requestReceivedLatch = new CountDownLatch(1);
        CountDownLatch pieceSentLatch = new CountDownLatch(1);
        
        try (MockPeerServer server = new MockPeerServer(localInfoHash, session -> {
            try {
                // 1. Handshake
                session.expectAndValidateHandshake();
                session.sendHandshake(localInfoHash, new byte[20]);
                
                // 2. Send Bitfield indicating we have piece 0
                session.sendBitfield(new byte[]{(byte) 0x80}); // Top bit is piece 0
                
                // Read next message (expecting INTERESTED = 2)
                java.lang.reflect.Field inField = session.getClass().getDeclaredField("in");
                inField.setAccessible(true);
                InputStream in = (InputStream) inField.get(session);
                
                // Just wait a bit and send UNCHOKE to trigger request
                Thread.sleep(500);
                session.sendUnchoke();
                
                // Wait to receive REQUEST (ID=6)
                byte[] lenBuf = new byte[4];
                int read = in.read(lenBuf);
                if (read == 4) {
                    int len = ByteBuffer.wrap(lenBuf).getInt();
                    if (len == 1) {
                        // Probably INTERESTED, read ID
                        in.read(); // Consume ID
                        
                        // Now read again for REQUEST
                        in.read(lenBuf);
                        len = ByteBuffer.wrap(lenBuf).getInt();
                    }
                    if (len == 13) {
                        int id = in.read();
                        if (id == 6) {
                            byte[] reqPayload = new byte[12];
                            in.read(reqPayload);
                            requestReceivedLatch.countDown();
                            
                            // Send PIECE back (ID=7)
                            ByteBuffer pieceMsg = ByteBuffer.allocate(8 + 16384);
                            pieceMsg.putInt(0); // index 0
                            pieceMsg.putInt(0); // offset 0
                            pieceMsg.put(new byte[16384]);
                            session.sendMessage(7, pieceMsg.array());
                            pieceSentLatch.countDown();
                        }
                    }
                }
                
                Thread.sleep(2000); // keep alive
            } catch (Exception e) {
                e.printStackTrace();
            }
        })) {
            PeerInfo peerInfo = new PeerInfo("127.0.0.1", server.getPort(), new byte[20]);
            peerManager.addPeers(Collections.singletonList(peerInfo));
            
            assertTrue(server.awaitConnection(2, TimeUnit.SECONDS), "Client did not connect");
            assertTrue(requestReceivedLatch.await(5, TimeUnit.SECONDS), "Server did not receive REQUEST");
            assertTrue(pieceSentLatch.await(2, TimeUnit.SECONDS), "Server did not send PIECE");
            
            // Allow time for BlockSelector/DownloadManager to process PIECE
            Thread.sleep(1000);
            
            // Piece should be processed and either VERIFIED or FAILED (failed in this case due to fake hash)
            // We just ensure it's not still in VERIFYING state, meaning assembly completed
            assertNotEquals(PieceState.VERIFYING, pieceManager.getLayout().getPiece(0).getState());
        }
    }
}
