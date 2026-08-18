package com.torrentx.peer;

import com.torrentx.network.Message;
import java.io.IOException;

/**
 * Manages the raw network connection socket, streams, and state machine transitions for a single Peer.
 */
public class PeerConnection {

    private final Peer peer;

    /**
     * Constructs a PeerConnection for a specific remote peer.
     */
    public PeerConnection(Peer peer) {
        this.peer = peer;
    }

    /**
     * Establishes a TCP connection and performs handshakes.
     *
     * @throws IOException if network failures occur.
     */
    public void connect() throws IOException {
        // TODO: Implement TCP socket connection and BitTorrent handshaking in future phases
        throw new UnsupportedOperationException("Peer connection establish is not implemented yet.");
    }

    /**
     * Closes the raw network connection socket.
     */
    public void disconnect() {
        // TODO: Implement disconnection and resource cleanups in future phases
        throw new UnsupportedOperationException("Peer disconnection is not implemented yet.");
    }

    /**
     * Sends a protocol message over the socket.
     *
     * @param message the message to send.
     * @throws IOException if writing to socket fails.
     */
    public void sendMessage(Message message) throws IOException {
        // TODO: Implement network writing logic in future phases
        throw new UnsupportedOperationException("Sending messages is not implemented yet.");
    }

    public Peer getPeer() {
        return peer;
    }
}
