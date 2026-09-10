package com.torrentx.peer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A deterministic local mock peer for integration testing.
 */
public class MockPeerServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final Thread serverThread;
    private volatile boolean running = true;
    private final byte[] expectedInfoHash;
    private CountDownLatch connectionLatch = new CountDownLatch(1);

    public MockPeerServer(byte[] expectedInfoHash, Consumer<MockPeerSession> sessionHandler) throws IOException {
        this.expectedInfoHash = expectedInfoHash;
        this.serverSocket = new ServerSocket(0);
        
        this.serverThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    connectionLatch.countDown();
                    MockPeerSession session = new MockPeerSession(clientSocket, expectedInfoHash);
                    sessionHandler.accept(session);
                } catch (IOException e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        });
        this.serverThread.start();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }
    
    public boolean awaitConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return connectionLatch.await(timeout, unit);
    }
    
    public void resetLatch() {
        this.connectionLatch = new CountDownLatch(1);
    }

    @Override
    public void close() throws Exception {
        running = false;
        serverSocket.close();
        serverThread.join(2000);
    }

    public static class MockPeerSession implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final byte[] expectedInfoHash;

        public MockPeerSession(Socket socket, byte[] expectedInfoHash) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            this.expectedInfoHash = expectedInfoHash;
        }

        public void expectAndValidateHandshake() throws IOException {
            byte[] incoming = new byte[68];
            int read = 0;
            while (read < 68) {
                int r = in.read(incoming, read, 68 - read);
                if (r == -1) throw new IOException("Unexpected EOF during handshake");
                read += r;
            }

            // Validate length and protocol identifier
            if (incoming[0] != 19) throw new IOException("Invalid protocol length");
            String protocol = new String(incoming, 1, 19);
            if (!"BitTorrent protocol".equals(protocol)) throw new IOException("Invalid protocol string");

            // Validate info hash
            byte[] receivedHash = Arrays.copyOfRange(incoming, 28, 48);
            if (!Arrays.equals(expectedInfoHash, receivedHash)) {
                throw new IOException("Info hash mismatch");
            }
        }

        public void sendHandshake(byte[] infoHash, byte[] peerId) throws IOException {
            PeerHandshake handshake = new PeerHandshake(infoHash, peerId);
            out.write(handshake.toByteBuffer().array());
            out.flush();
        }

        public void sendKeepAlive() throws IOException {
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();
        }

        public void sendChoke() throws IOException {
            sendMessage(0, new byte[0]);
        }

        public void sendUnchoke() throws IOException {
            sendMessage(1, new byte[0]);
        }

        public void sendInterested() throws IOException {
            sendMessage(2, new byte[0]);
        }

        public void sendNotInterested() throws IOException {
            sendMessage(3, new byte[0]);
        }

        public void sendHave(int pieceIndex) throws IOException {
            byte[] payload = new byte[4];
            ByteBuffer.wrap(payload).putInt(pieceIndex);
            sendMessage(4, payload);
        }

        public void sendBitfield(byte[] bitfield) throws IOException {
            sendMessage(5, bitfield);
        }
        
        public void sendMessage(int id, byte[] payload) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(5 + payload.length);
            buffer.putInt(1 + payload.length);
            buffer.put((byte) id);
            buffer.put(payload);
            out.write(buffer.array());
            out.flush();
        }

        public void sendMalformedMessage() throws IOException {
            // Choke but with payload
            ByteBuffer buffer = ByteBuffer.allocate(6);
            buffer.putInt(2); // Length 2
            buffer.put((byte) 0); // ID Choke
            buffer.put((byte) 0xFF); // Invalid payload
            out.write(buffer.array());
            out.flush();
        }

        public void sendTruncatedMessage() throws IOException {
            out.write(new byte[]{0, 0, 0, 5, 4, 0, 0}); // HAVE message, but only 2 bytes payload instead of 4
            out.flush();
        }

        public void disconnect() throws IOException {
            socket.close();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
