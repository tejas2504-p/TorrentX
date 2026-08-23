package com.torrentx.tracker;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackerResponseParserTest {

    @Test
    void testParseCompactPeers() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("d8:intervali1800e5:peers12:".getBytes(StandardCharsets.ISO_8859_1));
        // Peer 1: 192.168.1.100:6881 -> 192, 168, 1, 100, 0x1A, 0xE1
        bos.write(new byte[]{(byte) 192, (byte) 168, 1, 100, 0x1A, (byte) 0xE1});
        // Peer 2: 127.0.0.1:8080 -> 127, 0, 0, 1, 0x1F, 0x90
        bos.write(new byte[]{127, 0, 0, 1, 0x1F, (byte) 0x90});
        bos.write("e".getBytes(StandardCharsets.ISO_8859_1));

        byte[] data = bos.toByteArray();
        TrackerResponse response = TrackerResponseParser.parse(data);

        assertTrue(response.isSuccessful());
        assertEquals(1800, response.getInterval());
        List<PeerInfo> peers = response.getPeers();
        assertEquals(2, peers.size());

        PeerInfo p1 = peers.get(0);
        assertEquals("192.168.1.100", p1.getIp());
        assertEquals(6881, p1.getPort());
        assertNull(p1.getPeerId());

        PeerInfo p2 = peers.get(1);
        assertEquals("127.0.0.1", p2.getIp());
        assertEquals(8080, p2.getPort());
        assertNull(p2.getPeerId());
    }

    @Test
    void testParseDictionaryPeers() throws Exception {
        // Bencoded: d8:intervali900e5:peersld2:ip9:127.0.0.17:peer id20:-TX1000-abcdefghijkl4:porti6881eeee
        String bencodeStr = "d8:intervali900e5:peersld2:ip9:127.0.0.17:peer id20:-TX1000-abcdefghijkl4:porti6881eeee";
        byte[] data = bencodeStr.getBytes(StandardCharsets.ISO_8859_1);

        TrackerResponse response = TrackerResponseParser.parse(data);

        assertTrue(response.isSuccessful());
        assertEquals(900, response.getInterval());
        List<PeerInfo> peers = response.getPeers();
        assertEquals(1, peers.size());

        PeerInfo p = peers.get(0);
        assertEquals("127.0.0.1", p.getIp());
        assertEquals(6881, p.getPort());
        assertNotNull(p.getPeerId());
        assertEquals("-TX1000-abcdefghijkl", new String(p.getPeerId(), StandardCharsets.US_ASCII));
    }

    @Test
    void testParseFailureResponse() throws Exception {
        String bencodeStr = "d14:failure reason27:info_hash is not registerede";
        byte[] data = bencodeStr.getBytes(StandardCharsets.ISO_8859_1);

        TrackerResponse response = TrackerResponseParser.parse(data);

        assertFalse(response.isSuccessful());
        assertEquals("info_hash is not registered", response.getFailureReason());
        assertTrue(response.getPeers().isEmpty());
    }

    @Test
    void testParseMalformedResponse() {
        byte[] malformed = "d8:intervali1800e".getBytes(StandardCharsets.ISO_8859_1); // Missing closing 'e'
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(malformed));
    }
}
