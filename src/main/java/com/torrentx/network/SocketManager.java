package com.torrentx.network;

import java.io.IOException;
import java.net.Socket;

/**
 * Manages core TCP listener server socket and creates outbound TCP peer sockets.
 */
public class SocketManager {

    private final int port;

    /**
     * Constructs a SocketManager on a specific listening port.
     */
    public SocketManager(int port) {
        this.port = port;
    }

    /**
     * Starts listening for incoming peer connection requests on the configured port.
     *
     * @throws IOException if the server socket cannot open.
     */
    public void startListener() throws IOException {
        // TODO: Implement server socket accept loop in future phases
        throw new UnsupportedOperationException("Server socket listener is not implemented yet.");
    }

    /**
     * Stops the listener socket.
     *
     * @throws IOException if closing socket fails.
     */
    public void stopListener() throws IOException {
        // TODO: Implement server socket cleanup in future phases
        throw new UnsupportedOperationException("Server socket termination is not implemented yet.");
    }

    /**
     * Initiates an outbound TCP connection to a remote host.
     *
     * @param ip the destination host IP.
     * @param port the destination host port.
     * @return the connected TCP socket.
     * @throws IOException if connection fails.
     */
    public Socket connectTo(String ip, int port) throws IOException {
        // TODO: Implement socket connection client instantiation in future phases
        throw new UnsupportedOperationException("Outgoing peer connection is not implemented yet.");
    }

    public int getPort() {
        return port;
    }
}
