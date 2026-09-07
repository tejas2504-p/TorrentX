package com.torrentx.peer;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ProtocolHandler {
    private final byte[] localInfoHash;
    private final byte[] localPeerId;

    public ProtocolHandler(byte[] localInfoHash, byte[] localPeerId) {
        this.localInfoHash = localInfoHash;
        this.localPeerId = localPeerId;
    }

    public void handleConnect(PeerConnection connection) {
        connection.setState(PeerConnection.ConnectionState.HANDSHAKING);
        
        PeerHandshake handshake = new PeerHandshake(localInfoHash, localPeerId);
        connection.writeData(handshake.toByteBuffer());
    }

    public void handleRead(PeerConnection connection) {
        ByteBuffer readBuffer = connection.getReadBuffer();
        readBuffer.flip();

        try {
            while (readBuffer.hasRemaining()) {
                if (connection.getState() == PeerConnection.ConnectionState.HANDSHAKING) {
                    PeerHandshake handshake = PeerHandshake.parse(readBuffer);
                    if (handshake == null) {
                        break; // Need more data
                    }

                    if (!Arrays.equals(localInfoHash, handshake.getInfoHash())) {
                        throw new IllegalStateException("Info hash mismatch");
                    }

                    connection.setRemotePeerId(handshake.getPeerId());
                    connection.setState(PeerConnection.ConnectionState.ESTABLISHED);
                } else if (connection.getState() == PeerConnection.ConnectionState.ESTABLISHED) {
                    ByteBuffer payload = MessageCodec.decode(readBuffer);
                    if (payload == null) {
                        break; // Need more data
                    }
                    
                    handleMessage(connection, payload);
                } else {
                    break;
                }
            }
        } finally {
            readBuffer.compact();
        }
    }

    private void handleMessage(PeerConnection connection, ByteBuffer payload) {
        if (payload.remaining() == 0) {
            // Keep-alive, nothing to do besides update activity time (already done by PeerManager logic)
            return;
        }

        int messageId = payload.get() & 0xFF;
        // In Phase 5, we lay the foundation. Specific message handling will be implemented later.
        System.out.println("Received message ID: " + messageId + " from " + connection.getPeerInfo());
    }
}
