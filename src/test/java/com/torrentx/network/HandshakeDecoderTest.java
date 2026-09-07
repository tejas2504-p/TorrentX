package com.torrentx.network;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeDecoderTest {

    @Test
    void testDecodeValidHandshake() throws Exception {
        byte[] infoHash = new byte[20];
        Arrays.fill(infoHash, (byte) 0xAA);
        byte[] peerId = new byte[20];
        Arrays.fill(peerId, (byte) 0xBB);
        byte[] reserved = new byte[8];
        Arrays.fill(reserved, (byte) 0xCC);

        Handshake original = new Handshake(infoHash, peerId, reserved);
        
        // Use encoder to generate raw bytes
        byte[] rawBytes = new byte[68];
        rawBytes[0] = 19;
        System.arraycopy("BitTorrent protocol".getBytes(StandardCharsets.UTF_8), 0, rawBytes, 1, 19);
        System.arraycopy(reserved, 0, rawBytes, 20, 8);
        System.arraycopy(infoHash, 0, rawBytes, 28, 20);
        System.arraycopy(peerId, 0, rawBytes, 48, 20);

        ByteArrayInputStream in = new ByteArrayInputStream(rawBytes);
        Handshake decoded = HandshakeDecoder.decode(in);

        assertNotNull(decoded);
        assertArrayEquals(infoHash, decoded.getInfoHash());
        assertArrayEquals(peerId, decoded.getPeerId());
        assertArrayEquals(reserved, decoded.getReserved());
    }

    @Test
    void testDecodePartialTCPReads() throws Exception {
        // TCP is a stream and may return fewer bytes than requested.
        // We simulate this by creating an InputStream that returns 1 byte at a time.
        byte[] rawBytes = new byte[68];
        rawBytes[0] = 19;
        System.arraycopy("BitTorrent protocol".getBytes(StandardCharsets.UTF_8), 0, rawBytes, 1, 19);
        
        byte[] infoHash = new byte[20];
        Arrays.fill(infoHash, (byte) 0x11);
        System.arraycopy(infoHash, 0, rawBytes, 28, 20);
        
        InputStream fragmentedIn = new InputStream() {
            private int pos = 0;
            @Override
            public int read() {
                if (pos >= rawBytes.length) return -1;
                return rawBytes[pos++] & 0xFF;
            }
            @Override
            public int read(byte[] b, int off, int len) {
                if (pos >= rawBytes.length) return -1;
                // Force returning exactly 1 byte per read() call to simulate extreme TCP fragmentation
                b[off] = rawBytes[pos++];
                return 1;
            }
        };

        Handshake decoded = HandshakeDecoder.decode(fragmentedIn);
        assertNotNull(decoded);
        assertArrayEquals(infoHash, decoded.getInfoHash());
    }

    @Test
    void testDecodeInvalidProtocolLength() {
        byte[] rawBytes = new byte[68];
        rawBytes[0] = 20; // Invalid length, expecting 19
        ByteArrayInputStream in = new ByteArrayInputStream(rawBytes);

        ProtocolException ex = assertThrows(ProtocolException.class, () -> HandshakeDecoder.decode(in));
        assertTrue(ex.getMessage().contains("Invalid protocol string length: 20"));
    }

    @Test
    void testDecodeInvalidProtocolIdentifier() {
        byte[] rawBytes = new byte[68];
        rawBytes[0] = 19;
        System.arraycopy("InvalidProt protocol".getBytes(StandardCharsets.UTF_8), 0, rawBytes, 1, 19);
        ByteArrayInputStream in = new ByteArrayInputStream(rawBytes);

        ProtocolException ex = assertThrows(ProtocolException.class, () -> HandshakeDecoder.decode(in));
        assertTrue(ex.getMessage().contains("Unsupported protocol identifier"));
    }

    @Test
    void testDecodeTruncatedStream() {
        byte[] rawBytes = new byte[50]; // Truncated handshake
        rawBytes[0] = 19;
        System.arraycopy("BitTorrent protocol".getBytes(StandardCharsets.UTF_8), 0, rawBytes, 1, 19);
        ByteArrayInputStream in = new ByteArrayInputStream(rawBytes);

        assertThrows(java.io.EOFException.class, () -> HandshakeDecoder.decode(in));
    }
}
