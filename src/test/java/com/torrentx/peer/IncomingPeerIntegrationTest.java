package com.torrentx.peer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.torrentx.upload.UploadManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class IncomingPeerIntegrationTest {
    private PeerManager peerManager;
    private byte[] localInfoHash;
    private byte[] localPeerId;
    private UploadManager uploadManager;

    @BeforeEach
    void setUp() throws Exception {
        localInfoHash = new byte[20];
        Arrays.fill(localInfoHash, (byte) 7);
        
        localPeerId = new byte[20];
        Arrays.fill(localPeerId, (byte) 8);
        
        peerManager = new PeerManager(localInfoHash, localPeerId, 5);
        uploadManager = new UploadManager();
        peerManager.setUploadManager(uploadManager);
        peerManager.bind(0); // Random ephemeral port
        peerManager.start();
    }
    
    @AfterEach
    void tearDown() {
        if (peerManager != null) {
            peerManager.close();
        }
    }

    private Peer waitForPeerState(long timeoutMs) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < endTime) {
            Thread.sleep(50);
            if (!peerManager.getConnectedPeers().isEmpty()) {
                return peerManager.getConnectedPeers().get(0).getPeerState();
            }
        }
        return null;
    }

    private SocketChannel connectClient() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(true); // Blocking mode is fine for our mock client
        channel.connect(new InetSocketAddress("127.0.0.1", peerManager.getBoundPort()));
        return channel;
    }

    private ByteBuffer readHandshake(SocketChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(68);
        int totalRead = 0;
        while (totalRead < 68) {
            int r = channel.read(buf);
            if (r == -1) break;
            totalRead += r;
        }
        buf.flip();
        return buf;
    }

    @Test
    void testSuccessfulIncomingConnection() throws Exception {
        try (SocketChannel client = connectClient()) {
            // Read the handshake sent by PeerManager
            ByteBuffer response = readHandshake(client);
            assertEquals(68, response.remaining());
            PeerHandshake receivedHandshake = PeerHandshake.parse(response);
            assertNotNull(receivedHandshake);
            assertArrayEquals(localInfoHash, receivedHandshake.getInfoHash());

            // Send our handshake back
            byte[] clientPeerId = new byte[20];
            Arrays.fill(clientPeerId, (byte) 9);
            PeerHandshake clientHandshake = new PeerHandshake(localInfoHash, clientPeerId);
            client.write(clientHandshake.toByteBuffer());

            // PeerManager should parse this and transition to READY
            Peer p = waitForPeerState(2000);
            assertNotNull(p);
            
            // Wait for handshake to be processed and state transition to READY
            long timeout = System.currentTimeMillis() + 2000;
            while (peerManager.getConnectedPeers().get(0).getState() != PeerConnectionState.READY && System.currentTimeMillis() < timeout) {
                Thread.sleep(50);
            }
            assertEquals(PeerConnectionState.READY, peerManager.getConnectedPeers().get(0).getState());
            assertEquals(1, uploadManager.getRegisteredPeerCount());
        }
    }

    @Test
    void testWrongInfoHash() throws Exception {
        try (SocketChannel client = connectClient()) {
            ByteBuffer response = readHandshake(client);
            assertEquals(68, response.remaining());

            byte[] wrongHash = new byte[20];
            Arrays.fill(wrongHash, (byte) 2);
            PeerHandshake clientHandshake = new PeerHandshake(wrongHash, new byte[20]);
            client.write(clientHandshake.toByteBuffer());

            // Wait a bit, connection should be dropped by PeerManager
            Thread.sleep(500);
            assertEquals(0, peerManager.getConnectedPeers().size());
            assertEquals(0, uploadManager.getRegisteredPeerCount());
        }
    }

    @Test
    void testMalformedHandshake() throws Exception {
        try (SocketChannel client = connectClient()) {
            ByteBuffer response = readHandshake(client);
            assertEquals(68, response.remaining());

            ByteBuffer malformed = ByteBuffer.allocate(68);
            malformed.put((byte) 19);
            malformed.put("BitTorrent protocol".getBytes());
            malformed.put(new byte[8]); // Reserved
            // Send truncated info hash
            malformed.put(new byte[10]); 
            malformed.flip();
            
            client.write(malformed);
            client.close(); // Disconnect unexpectedly

            Thread.sleep(500);
            assertEquals(0, peerManager.getConnectedPeers().size());
        }
    }

    @Test
    void testMultipleIncomingPeers() throws Exception {
        try (SocketChannel client1 = connectClient(); SocketChannel client2 = connectClient()) {
            readHandshake(client1);
            readHandshake(client2);

            byte[] pid1 = new byte[20]; Arrays.fill(pid1, (byte) 1);
            client1.write(new PeerHandshake(localInfoHash, pid1).toByteBuffer());

            byte[] pid2 = new byte[20]; Arrays.fill(pid2, (byte) 2);
            client2.write(new PeerHandshake(localInfoHash, pid2).toByteBuffer());

            Thread.sleep(1000);
            assertEquals(2, peerManager.getConnectedPeers().size());
            assertEquals(2, uploadManager.getRegisteredPeerCount());
        }
    }
}
