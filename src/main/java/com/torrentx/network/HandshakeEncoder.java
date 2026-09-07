package com.torrentx.network;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HandshakeEncoder {
    public static final int HANDSHAKE_LENGTH = 68;

    /**
     * Encodes a BitTorrent Handshake into a 68-byte array.
     * 
     * @param handshake a valid Handshake object
     * @return exactly 68 bytes containing the encoded handshake
     */
    public static byte[] encode(Handshake handshake) {
        if (handshake == null) {
            throw new IllegalArgumentException("Handshake cannot be null");
        }

        ByteBuffer buffer = ByteBuffer.allocate(HANDSHAKE_LENGTH);
        
        byte[] pstrBytes = Handshake.PROTOCOL_IDENTIFIER.getBytes(StandardCharsets.UTF_8);
        
        // 1. Protocol string length: 1 byte
        buffer.put((byte) pstrBytes.length); // 19
        
        // 2. "BitTorrent protocol": 19 bytes
        buffer.put(pstrBytes);
        
        // 3. Reserved bytes: 8 bytes (all zeroes by default)
        buffer.put(new byte[8]);
        
        // 4. Info hash: 20 bytes
        buffer.put(handshake.getInfoHash());
        
        // 5. Peer ID: 20 bytes
        buffer.put(handshake.getPeerId());
        
        return buffer.array();
    }
}
