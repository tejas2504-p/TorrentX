package com.torrentx.peer;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PeerHandshakeTest {

    @Test
    void testEncodeAndParse() {
        byte[] infoHash = new byte[20];
        Arrays.fill(infoHash, (byte) 1);
        byte[] peerId = new byte[20];
        Arrays.fill(peerId, (byte) 2);

        PeerHandshake original = new PeerHandshake(infoHash, peerId);
        ByteBuffer buffer = original.toByteBuffer();

        assertEquals(68, buffer.remaining());

        PeerHandshake parsed = PeerHandshake.parse(buffer);

        assertNotNull(parsed);
        assertArrayEquals(infoHash, parsed.getInfoHash());
        assertArrayEquals(peerId, parsed.getPeerId());
        assertArrayEquals(new byte[8], parsed.getReserved());
    }

    @Test
    void testParseIncompleteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(50); // Less than 68
        assertNull(PeerHandshake.parse(buffer));
    }

    @Test
    void testParseInvalidProtocol() {
        ByteBuffer buffer = ByteBuffer.allocate(68);
        buffer.put((byte) 19);
        buffer.put("Invalid Protocol!!!".getBytes()); // 19 bytes
        buffer.position(0);

        assertThrows(IllegalArgumentException.class, () -> PeerHandshake.parse(buffer));
    }

    @Test
    void testInvalidInfoHashLength() {
        byte[] invalidInfoHash = new byte[19];
        byte[] peerId = new byte[20];
        assertThrows(IllegalArgumentException.class, () -> new PeerHandshake(invalidInfoHash, peerId));
    }

    @Test
    void testInvalidPeerIdLength() {
        byte[] infoHash = new byte[20];
        byte[] invalidPeerId = new byte[21];
        assertThrows(IllegalArgumentException.class, () -> new PeerHandshake(infoHash, invalidPeerId));
    }

    @Test
    void testIncorrectFieldLengths() {
        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];
        byte[] invalidReserved = new byte[7];
        assertThrows(IllegalArgumentException.class, () -> new PeerHandshake(infoHash, peerId, invalidReserved));
    }

    @Test
    void testDefensiveCopying() {
        byte[] infoHash = new byte[20];
        Arrays.fill(infoHash, (byte) 1);
        byte[] peerId = new byte[20];
        Arrays.fill(peerId, (byte) 2);
        
        PeerHandshake handshake = new PeerHandshake(infoHash, peerId);
        
        // Mutate original arrays
        infoHash[0] = 99;
        peerId[0] = 99;
        
        // Handshake should not be affected
        assertNotEquals((byte) 99, handshake.getInfoHash()[0]);
        assertNotEquals((byte) 99, handshake.getPeerId()[0]);
        
        // Mutate arrays returned by getters
        byte[] retrievedInfoHash = handshake.getInfoHash();
        retrievedInfoHash[0] = 55;
        
        // Internal state should not be affected
        assertNotEquals((byte) 55, handshake.getInfoHash()[0]);
    }
}
