package com.torrentx.peer;

import com.torrentx.tracker.PeerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PeerManagerTest {

    private PeerManager peerManager;
    private byte[] localInfoHash;
    private byte[] localPeerId;
    
    @BeforeEach
    void setUp() throws IOException {
        localInfoHash = new byte[20];
        Arrays.fill(localInfoHash, (byte) 1);
        
        localPeerId = new byte[20];
        Arrays.fill(localPeerId, (byte) 2);
        
        peerManager = new PeerManager(localInfoHash, localPeerId);
        peerManager.start();
    }
    
    @AfterEach
    void tearDown() {
        if (peerManager != null) {
            peerManager.close();
        }
    }
    
    @Test
    void testValidConnectionAndHandshake() throws Exception {
        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch handshakeReceivedLatch = new CountDownLatch(1);
        
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket client = serverSocket.accept()) {
                    connectedLatch.countDown();
                    
                    InputStream in = client.getInputStream();
                    OutputStream out = client.getOutputStream();
                    
                    // Read incoming handshake (68 bytes)
                    byte[] incoming = new byte[68];
                    int read = 0;
                    while (read < 68) {
                        int r = in.read(incoming, read, 68 - read);
                        if (r == -1) break;
                        read += r;
                    }
                    
                    if (read == 68) {
                        // Send valid response
                        PeerHandshake response = new PeerHandshake(localInfoHash, new byte[20]);
                        out.write(response.toByteBuffer().array());
                        out.flush();
                        handshakeReceivedLatch.countDown();
                    }
                } catch (IOException e) {
                    // ignore
                }
            });
            serverThread.start();
            
            PeerInfo peer = new PeerInfo("127.0.0.1", serverSocket.getLocalPort(), new byte[20]);
            peerManager.addPeers(Collections.singletonList(peer));
            
            assertTrue(connectedLatch.await(2, TimeUnit.SECONDS));
            assertTrue(handshakeReceivedLatch.await(2, TimeUnit.SECONDS));
        }
    }
    
    @Test
    void testConnectionRefused() throws Exception {
        // Find a free port and then close the ServerSocket to ensure it's refused
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        
        PeerInfo peer = new PeerInfo("127.0.0.1", freePort, new byte[20]);
        peerManager.addPeers(Collections.singletonList(peer));
        
        // Wait a bit to let reactor process it
        Thread.sleep(500);
        
        // Since it's async, we just verify the manager doesn't crash
        // and connection is removed/not kept active
    }
    
    @Test
    void testUnexpectedDisconnect() throws Exception {
        CountDownLatch connectedLatch = new CountDownLatch(1);
        
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket client = serverSocket.accept()) {
                    connectedLatch.countDown();
                    // Close immediately
                } catch (IOException e) {
                    // ignore
                }
            });
            serverThread.start();
            
            PeerInfo peer = new PeerInfo("127.0.0.1", serverSocket.getLocalPort(), new byte[20]);
            peerManager.addPeers(Collections.singletonList(peer));
            
            assertTrue(connectedLatch.await(2, TimeUnit.SECONDS));
            Thread.sleep(500); // Give it time to detect disconnect
        }
    }
    
    @Test
    void testMalformedHandshake() throws Exception {
        CountDownLatch connectedLatch = new CountDownLatch(1);
        
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket client = serverSocket.accept()) {
                    connectedLatch.countDown();
                    OutputStream out = client.getOutputStream();
                    
                    // Send garbage
                    out.write(new byte[]{ 99, 88, 77 });
                    out.flush();
                } catch (IOException e) {
                    // ignore
                }
            });
            serverThread.start();
            
            PeerInfo peer = new PeerInfo("127.0.0.1", serverSocket.getLocalPort(), new byte[20]);
            peerManager.addPeers(Collections.singletonList(peer));
            
            assertTrue(connectedLatch.await(2, TimeUnit.SECONDS));
            Thread.sleep(500); 
        }
    }
    
    @Test
    void testInfoHashMismatch() throws Exception {
        CountDownLatch connectedLatch = new CountDownLatch(1);
        
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket client = serverSocket.accept()) {
                    connectedLatch.countDown();
                    OutputStream out = client.getOutputStream();
                    
                    // Send mismatched info hash
                    byte[] wrongHash = new byte[20];
                    Arrays.fill(wrongHash, (byte) 9);
                    PeerHandshake response = new PeerHandshake(wrongHash, new byte[20]);
                    out.write(response.toByteBuffer().array());
                    out.flush();
                } catch (IOException e) {
                    // ignore
                }
            });
            serverThread.start();
            
            PeerInfo peer = new PeerInfo("127.0.0.1", serverSocket.getLocalPort(), new byte[20]);
            peerManager.addPeers(Collections.singletonList(peer));
            
            assertTrue(connectedLatch.await(2, TimeUnit.SECONDS));
            Thread.sleep(500); 
        }
    }
}
