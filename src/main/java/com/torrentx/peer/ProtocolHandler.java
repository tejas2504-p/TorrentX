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
        connection.transitionState(PeerConnectionState.CONNECTED);
        connection.transitionState(PeerConnectionState.HANDSHAKING);
        
        PeerHandshake handshake = new PeerHandshake(localInfoHash, localPeerId);
        connection.writeData(handshake.toByteBuffer());
    }

    public void handleRead(PeerConnection connection) {
        ByteBuffer readBuffer = connection.getReadBuffer();
        readBuffer.flip();

        try {
            while (readBuffer.hasRemaining()) {
                if (connection.getState() == PeerConnectionState.HANDSHAKING) {
                    PeerHandshake handshake = PeerHandshake.parse(readBuffer);
                    if (handshake == null) {
                        break; // Need more data
                    }

                    if (!Arrays.equals(localInfoHash, handshake.getInfoHash())) {
                        throw new IllegalStateException("Info hash mismatch");
                    }

                    connection.setRemotePeerId(handshake.getPeerId());
                    connection.transitionState(PeerConnectionState.READY);
                } else if (connection.getState() == PeerConnectionState.READY) {
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

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(ProtocolHandler.class.getName());

    private void handleMessage(PeerConnection connection, ByteBuffer payload) {
        if (payload.remaining() == 0) {
            // Keep-alive, nothing to do besides update activity time (already done by PeerManager logic)
            LOGGER.fine("Received keep-alive from " + connection.getPeerInfo());
            return;
        }

        int messageId = payload.get() & 0xFF;
        Peer peer = connection.getPeerState();
        
        try {
            switch (messageId) {
                case 0: // choke
                    if (payload.remaining() != 0) throw new IllegalStateException("Choke message must have 0-byte payload");
                    peer.setChokingMe(true);
                    LOGGER.info("Peer " + connection.getPeerInfo() + " choked us");
                    break;
                case 1: // unchoke
                    if (payload.remaining() != 0) throw new IllegalStateException("Unchoke message must have 0-byte payload");
                    peer.setChokingMe(false);
                    LOGGER.info("Peer " + connection.getPeerInfo() + " unchoked us");
                    break;
                case 2: // interested
                    if (payload.remaining() != 0) throw new IllegalStateException("Interested message must have 0-byte payload");
                    peer.setInterestedInMe(true);
                    LOGGER.info("Peer " + connection.getPeerInfo() + " is interested in us");
                    break;
                case 3: // not interested
                    if (payload.remaining() != 0) throw new IllegalStateException("Not interested message must have 0-byte payload");
                    peer.setInterestedInMe(false);
                    LOGGER.info("Peer " + connection.getPeerInfo() + " is not interested in us");
                    break;
                case 4: // have
                    if (payload.remaining() != 4) throw new IllegalStateException("Have message must have 4-byte payload");
                    int pieceIndex = payload.getInt();
                    LOGGER.info("Peer " + connection.getPeerInfo() + " has piece " + pieceIndex);
                    // Piece availability tracking will be added later
                    break;
                case 5: // bitfield
                    byte[] bitfield = new byte[payload.remaining()];
                    payload.get(bitfield);
                    LOGGER.info("Peer " + connection.getPeerInfo() + " sent bitfield of length " + bitfield.length);
                    // Bitfield tracking will be added later
                    break;
                default:
                    LOGGER.warning("Received unhandled or unknown message ID: " + messageId + " from " + connection.getPeerInfo());
                    break;
            }
        } catch (Exception e) {
            LOGGER.warning("Malformed message from " + connection.getPeerInfo() + ": " + e.getMessage());
            // Optionally we could disconnect, but for now we just log and ignore the malformed message
            throw new IllegalArgumentException("Malformed message", e);
        }
    }
}
