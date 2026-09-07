package com.torrentx.network;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class HandshakeDecoder {
    
    /**
     * Decodes a BitTorrent Handshake from an InputStream.
     * Blocks until exactly 68 bytes are read or EOF is reached.
     *
     * @param in the input stream to read from
     * @return a valid Handshake object
     * @throws IOException if an I/O error occurs or the stream is closed prematurely
     * @throws ProtocolException if the handshake format is invalid
     */
    public static Handshake decode(InputStream in) throws IOException, ProtocolException {
        // We use DataInputStream to guarantee we can block until the requested bytes are read,
        // fulfilling the stream requirement that one read() might return partial data.
        DataInputStream dataIn = new DataInputStream(in);
        
        int pstrlen = dataIn.readUnsignedByte();
        if (pstrlen != Handshake.PROTOCOL_IDENTIFIER.length()) { // Expecting 19
            throw new ProtocolException("Invalid protocol string length: " + pstrlen);
        }
        
        byte[] pstrBytes = new byte[pstrlen];
        dataIn.readFully(pstrBytes); // Will block until 19 bytes are read
        
        String pstr = new String(pstrBytes, StandardCharsets.UTF_8);
        if (!Handshake.PROTOCOL_IDENTIFIER.equals(pstr)) {
            throw new ProtocolException("Unsupported protocol identifier: " + pstr);
        }
        
        byte[] reserved = new byte[8];
        dataIn.readFully(reserved);
        
        byte[] infoHash = new byte[20];
        dataIn.readFully(infoHash);
        
        byte[] peerId = new byte[20];
        dataIn.readFully(peerId);
        
        return new Handshake(infoHash, peerId, reserved);
    }
}
