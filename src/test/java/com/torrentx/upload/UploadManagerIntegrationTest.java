package com.torrentx.upload;

import com.torrentx.peer.PeerConnectionState;
import com.torrentx.peer.PeerHandshake;
import com.torrentx.peer.PeerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UploadManagerIntegrationTest {
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

        // Max 2 upload slots to test slot limiting
        uploadManager = new UploadManager(2);
        
        peerManager = new PeerManager(localInfoHash, localPeerId, 50);
        peerManager.setUploadManager(uploadManager);
        peerManager.bind(0); 
        peerManager.start();
        uploadManager.start();
    }

    @AfterEach
    void tearDown() {
        if (uploadManager != null) {
            uploadManager.close();
        }
        if (peerManager != null) {
            peerManager.close();
        }
    }

    private SocketChannel connectClientAndHandshake(byte[] peerId) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(new InetSocketAddress("127.0.0.1", peerManager.getBoundPort()));

        // Read our peer manager's handshake
        ByteBuffer buf = ByteBuffer.allocate(68);
        while (buf.hasRemaining()) {
            if (channel.read(buf) == -1) break;
        }

        // Send handshake
        PeerHandshake clientHandshake = new PeerHandshake(localInfoHash, peerId);
        channel.write(clientHandshake.toByteBuffer());

        return channel;
    }

    private void sendInterested(SocketChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.putInt(1);
        buf.put((byte) 2); // interested
        buf.flip();
        channel.write(buf);
    }
    
    private void sendNotInterested(SocketChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.putInt(1);
        buf.put((byte) 3); // not interested
        buf.flip();
        channel.write(buf);
    }

    private boolean isUnchokeMessage(ByteBuffer msg) {
        if (msg.remaining() < 5) return false;
        int len = msg.getInt();
        if (len != 1) return false;
        byte id = msg.get();
        return id == 1; // 1 = unchoke
    }
    
    private boolean isChokeMessage(ByteBuffer msg) {
        if (msg.remaining() < 5) return false;
        int len = msg.getInt();
        if (len != 1) return false;
        byte id = msg.get();
        return id == 0; // 0 = choke
    }

    private ByteBuffer readMessage(SocketChannel channel) throws IOException {
        channel.configureBlocking(false); // Make non-blocking to prevent infinite hang
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        long timeout = System.currentTimeMillis() + 2000;
        
        while (lenBuf.hasRemaining() && System.currentTimeMillis() < timeout) {
            channel.read(lenBuf);
            try { Thread.sleep(10); } catch (InterruptedException e) {}
        }
        
        if (lenBuf.hasRemaining()) return null; // Timeout
        
        lenBuf.flip();
        int len = lenBuf.getInt();
        if (len == 0) return ByteBuffer.allocate(0); // keep-alive
        
        ByteBuffer payloadBuf = ByteBuffer.allocate(len);
        timeout = System.currentTimeMillis() + 2000;
        while (payloadBuf.hasRemaining() && System.currentTimeMillis() < timeout) {
            channel.read(payloadBuf);
            try { Thread.sleep(10); } catch (InterruptedException e) {}
        }
        
        if (payloadBuf.hasRemaining()) return null; // Timeout
        payloadBuf.flip();
        
        ByteBuffer fullMsg = ByteBuffer.allocate(4 + len);
        fullMsg.putInt(len);
        fullMsg.put(payloadBuf);
        fullMsg.flip();
        channel.configureBlocking(true); // Restore
        return fullMsg;
    }

    @Test
    void testInterestedUnchokesPeer() throws Exception {
        byte[] pid = new byte[20]; Arrays.fill(pid, (byte) 1);
        try (SocketChannel client = connectClientAndHandshake(pid)) {
            Thread.sleep(200); // Wait for READY
            
            // Send interested
            sendInterested(client);
            
            // Should receive an unchoke message
            ByteBuffer msg = readMessage(client);
            assertNotNull(msg, "Should receive unchoke message");
            assertTrue(isUnchokeMessage(msg));
            
            assertEquals(1, peerManager.getConnectedPeers().size());
            assertFalse(peerManager.getConnectedPeers().get(0).getPeerState().isChoked());
        }
    }
    
    @Test
    void testNotInterestedChokesPeer() throws Exception {
        byte[] pid = new byte[20]; Arrays.fill(pid, (byte) 2);
        try (SocketChannel client = connectClientAndHandshake(pid)) {
            Thread.sleep(200);
            
            // Send interested, wait for unchoke
            sendInterested(client);
            ByteBuffer unchokeMsg = readMessage(client);
            assertNotNull(unchokeMsg);
            assertTrue(isUnchokeMessage(unchokeMsg));
            
            // Now send not interested, should receive choke
            sendNotInterested(client);
            ByteBuffer chokeMsg = readMessage(client);
            assertNotNull(chokeMsg);
            assertTrue(isChokeMessage(chokeMsg));
            
            assertTrue(peerManager.getConnectedPeers().get(0).getPeerState().isChoked());
        }
    }

    @Test
    void testUploadSlotLimits() throws Exception {
        List<SocketChannel> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                byte[] pid = new byte[20]; Arrays.fill(pid, (byte) (i + 1));
                SocketChannel c = connectClientAndHandshake(pid);
                clients.add(c);
                Thread.sleep(100);
            }
            
            assertEquals(3, peerManager.getConnectedPeers().size());
            
            // All 3 become interested
            for (SocketChannel c : clients) {
                sendInterested(c);
            }
            
            // Wait for recalculate
            Thread.sleep(500);
            
            int unchokedCount = 0;
            for (int i = 0; i < peerManager.getConnectedPeers().size(); i++) {
                if (!peerManager.getConnectedPeers().get(i).getPeerState().isChoked()) {
                    unchokedCount++;
                }
            }
            
            // Max slots is 2, so only 2 should be unchoked
            assertEquals(2, unchokedCount, "Only 2 peers should be unchoked despite 3 interested");
        } finally {
            for (SocketChannel c : clients) {
                c.close();
            }
        }
    }

    @Test
    void testDisconnectRemovesPeerAndFreesSlot() throws Exception {
        List<SocketChannel> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                byte[] pid = new byte[20]; Arrays.fill(pid, (byte) (i + 1));
                SocketChannel c = connectClientAndHandshake(pid);
                clients.add(c);
                Thread.sleep(100);
            }
            
            for (SocketChannel c : clients) {
                sendInterested(c);
            }
            
            Thread.sleep(500);
            
            // Close one of the unchoked peers
            boolean closed = false;
            for (int i = 0; i < peerManager.getConnectedPeers().size(); i++) {
                if (!peerManager.getConnectedPeers().get(i).getPeerState().isChoked()) {
                    clients.get(i).close();
                    closed = true;
                    break;
                }
            }
            assertTrue(closed);
            
            // Wait for disconnect handling and recalculateSlots
            Thread.sleep(1000);
            
            assertEquals(2, peerManager.getConnectedPeers().size());
            
            int unchokedCount = 0;
            for (int i = 0; i < peerManager.getConnectedPeers().size(); i++) {
                if (!peerManager.getConnectedPeers().get(i).getPeerState().isChoked()) {
                    unchokedCount++;
                }
            }
            
            // Both remaining peers should now be unchoked (2 slots available)
            assertEquals(2, unchokedCount, "Remaining 2 peers should both be unchoked");
        } finally {
            for (SocketChannel c : clients) {
                if (c.isOpen()) c.close();
            }
        }
    }
}
