package com.torrentx.network;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeEncoderTest {

    @Test
    void testEncodeValidHandshake() {
        byte[] infoHash = new byte[20];
        Arrays.fill(infoHash, (byte) 0xAA);
        byte[] peerId = new byte[20];
        Arrays.fill(peerId, (byte) 0xBB);

        Handshake handshake = new Handshake(infoHash, peerId);
        byte[] encoded = HandshakeEncoder.encode(handshake);

        assertNotNull(encoded);
        assertEquals(68, encoded.length);

        // Byte 0: 19
        assertEquals(19, encoded[0]);

        // Bytes 1-19: "BitTorrent protocol"
        String pstr = new String(Arrays.copyOfRange(encoded, 1, 20), StandardCharsets.UTF_8);
        assertEquals("BitTorrent protocol", pstr);

        // Bytes 20-27: 8 reserved bytes (all zero)
        byte[] reserved = Arrays.copyOfRange(encoded, 20, 28);
        assertArrayEquals(new byte[8], reserved);

        // Bytes 28-47: 20-byte info_hash
        byte[] encodedInfoHash = Arrays.copyOfRange(encoded, 28, 48);
        assertArrayEquals(infoHash, encodedInfoHash);

        // Bytes 48-67: 20-byte peer_id
        byte[] encodedPeerId = Arrays.copyOfRange(encoded, 48, 68);
        assertArrayEquals(peerId, encodedPeerId);
    }

    @Test
    void testEncodePreservesRawBinaryData() {
        // Test with non-ASCII and null bytes to ensure binary integrity
        byte[] infoHash = new byte[20];
        for (int i = 0; i < 20; i++) {
            infoHash[i] = (byte) i; // 0x00 to 0x13
        }
        
        byte[] peerId = new byte[20];
        for (int i = 0; i < 20; i++) {
            peerId[i] = (byte) (0xFF - i); // High bits set
        }

        Handshake handshake = new Handshake(infoHash, peerId);
        byte[] encoded = HandshakeEncoder.encode(handshake);

        assertArrayEquals(infoHash, Arrays.copyOfRange(encoded, 28, 48));
        assertArrayEquals(peerId, Arrays.copyOfRange(encoded, 48, 68));
    }

    @Test
    void testEncodeNullHandshakeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> HandshakeEncoder.encode(null));
    }
}
