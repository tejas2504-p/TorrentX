package com.torrentx.network;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface defining protocol encoding and decoding contracts for socket message formatting.
 */
public interface ProtocolHandler {

    /**
     * Reads from input stream and parses a complete protocol Message.
     *
     * @param in the socket input stream.
     * @return the parsed Message.
     * @throws Exception if decoding fails or connection drops.
     */
    Message readMessage(InputStream in) throws Exception;

    /**
     * Encodes and writes a Message to the socket output stream.
     *
     * @param message the message to encode.
     * @param out the socket output stream.
     * @throws Exception if encoding or writing fails.
     */
    void writeMessage(Message message, OutputStream out) throws Exception;
}
