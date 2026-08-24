package com.torrentx.tracker;
 
import org.junit.jupiter.api.Test;
 
import java.io.ByteArrayOutputStream;
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
        assertNull(response.getWarningMessage());
        assertTrue(response.getPeers().isEmpty());
    }
 
    @Test
    void testParseFailureResponseWithWarning() throws Exception {
        String bencodeStr = "d14:failure reason27:info_hash is not registered15:warning message12:some warninge";
        byte[] data = bencodeStr.getBytes(StandardCharsets.ISO_8859_1);
 
        TrackerResponse response = TrackerResponseParser.parse(data);
 
        assertFalse(response.isSuccessful());
        assertEquals("info_hash is not registered", response.getFailureReason());
        assertEquals("some warning", response.getWarningMessage());
    }
 
    @Test
    void testParseSuccessResponseWithAllOptionalFields() throws Exception {
        String bencodeStr = "d8:completei10e10:incompletei5e8:intervali1800e12:min intervali900e5:peers0:10:tracker id13:tracker_12345e";
        byte[] data = bencodeStr.getBytes(StandardCharsets.ISO_8859_1);
 
        TrackerResponse response = TrackerResponseParser.parse(data);
 
        assertTrue(response.isSuccessful());
        assertEquals(1800, response.getInterval());
        assertEquals(900, response.getMinInterval());
        assertEquals(10, response.getComplete());
        assertEquals(5, response.getIncomplete());
        assertEquals("tracker_12345", response.getTrackerId());
        assertTrue(response.getPeers().isEmpty());
    }
 
    @Test
    void testParseNullData() {
        assertThrows(IllegalArgumentException.class, () -> TrackerResponseParser.parse(null));
    }
 
    @Test
    void testParseMalformedBencode() {
        byte[] malformed = "d8:intervali1800e".getBytes(StandardCharsets.ISO_8859_1); // Missing closing 'e'
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(malformed));
    }
 
    @Test
    void testParseRootNotDictionary() {
        byte[] notDict = "li1800ee".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(notDict));
    }
 
    @Test
    void testParseMissingRequiredFields() {
        // Missing peers
        byte[] missingPeers = "d8:intervali1800ee".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(missingPeers));
 
        // Missing interval
        byte[] missingInterval = "d5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(missingInterval));
    }
 
    @Test
    void testParseInvalidTypes() {
        // Interval as string (should be integer/number)
        byte[] badIntervalType = "d8:interval5:1800e5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badIntervalType));
 
        // Warning message as integer
        byte[] badWarningType = "d8:intervali1800e5:peers0:15:warning messagei123ee".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badWarningType));
 
        // Tracker ID as integer
        byte[] badTrackerIdType = "d8:intervali1800e5:peers0:10:tracker idi123ee".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badTrackerIdType));
 
        // Complete count as string
        byte[] badCompleteType = "d8:complete5:tens8:intervali1800e5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badCompleteType));
 
        // Incomplete count as string
        byte[] badIncompleteType = "d10:incomplete4:five8:intervali1800e5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badIncompleteType));
 
        // Peers as integer
        byte[] badPeersType = "d8:intervali1800e5:peersi123ee".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badPeersType));
    }
 
    @Test
    void testParseNegativeValues() {
        // Negative interval
        byte[] negInterval = "d8:intervali-1800e5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(negInterval));
 
        // Negative min interval
        byte[] negMinInterval = "d8:intervali1800e12:min intervali-900e5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(negMinInterval));
 
        // Negative complete count
        byte[] negComplete = "d8:completei-10e8:intervali1800e5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(negComplete));
 
        // Negative incomplete count
        byte[] negIncomplete = "d10:incompletei-5e8:intervali1800e5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(negIncomplete));
    }
 
    @Test
    void testParseCompactPeersLengthValidation() {
        // Compact peers must be multiple of 6 (here 5 bytes)
        byte[] badCompactLen = "d8:intervali1800e5:peers5:12345e".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badCompactLen));
    }
 
    @Test
    void testParseCompactPeersInvalidPort() throws Exception {
        // Port 0 is invalid
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("d8:intervali1800e5:peers6:".getBytes(StandardCharsets.ISO_8859_1));
        bos.write(new byte[]{127, 0, 0, 1, 0, 0});
        bos.write("e".getBytes(StandardCharsets.ISO_8859_1));
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(bos.toByteArray()));
    }
 
    @Test
    void testParseDictionaryPeersValidation() {
        // List elements must be maps
        String badListElement = "d8:intervali1800e5:peersli123eee";
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badListElement.getBytes(StandardCharsets.ISO_8859_1)));
 
        // Peer entry missing ip
        String missingIp = "d8:intervali1800e5:peersld4:porti6881eeee";
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(missingIp.getBytes(StandardCharsets.ISO_8859_1)));
 
        // Peer entry ip is integer
        String badIpType = "d8:intervali1800e5:peersld2:ipi123e4:porti6881eeee";
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badIpType.getBytes(StandardCharsets.ISO_8859_1)));
 
        // Peer entry missing port
        String missingPort = "d8:intervali1800e5:peersld2:ip9:127.0.0.1eee";
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(missingPort.getBytes(StandardCharsets.ISO_8859_1)));
 
        // Peer entry port is string
        String badPortType = "d8:intervali1800e5:peersld2:ip9:127.0.0.14:port4:6881eee";
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badPortType.getBytes(StandardCharsets.ISO_8859_1)));
 
        // Peer entry port out of range (0)
        String zeroPort = "d8:intervali1800e5:peersld2:ip9:127.0.0.14:porti0eeee";
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(zeroPort.getBytes(StandardCharsets.ISO_8859_1)));
 
        // Peer entry port out of range (70000)
        String hugePort = "d8:intervali1800e5:peersld2:ip9:127.0.0.14:porti70000eeee";
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(hugePort.getBytes(StandardCharsets.ISO_8859_1)));
 
        // Peer entry bad peer id type (integer)
        String badPeerIdType = "d8:intervali1800e5:peersld2:ip9:127.0.0.14:porti6881e7:peer idi123eeee";
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parse(badPeerIdType.getBytes(StandardCharsets.ISO_8859_1)));
    }
 
    @Test
    void testParseDictionaryPeersWithRawBinaryPeerId() throws Exception {
        // peer id contains raw non-ASCII bytes
        byte[] rawPeerId = new byte[]{1, 2, 3, 4, 5, (byte) 255, (byte) 128, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("d8:intervali1800e5:peersld2:ip9:127.0.0.17:peer id20:".getBytes(StandardCharsets.ISO_8859_1));
        bos.write(rawPeerId);
        bos.write("4:porti6881eeee".getBytes(StandardCharsets.ISO_8859_1));
 
        byte[] data = bos.toByteArray();
        TrackerResponse response = TrackerResponseParser.parse(data);
 
        assertTrue(response.isSuccessful());
        List<PeerInfo> peers = response.getPeers();
        assertEquals(1, peers.size());
        PeerInfo p = peers.get(0);
        assertEquals("127.0.0.1", p.getIp());
        assertEquals(6881, p.getPort());
        assertArrayEquals(rawPeerId, p.getPeerId());
    }
 
    @Test
    void testParseCompactPeersNull() {
        assertThrows(IllegalArgumentException.class, () -> TrackerResponseParser.parseCompactPeers(null));
    }
 
    @Test
    void testParseCompactPeersZeroPeers() throws Exception {
        byte[] data = new byte[0];
        List<PeerInfo> peers = TrackerResponseParser.parseCompactPeers(data);
        assertNotNull(peers);
        assertTrue(peers.isEmpty());
    }
 
    @Test
    void testParseCompactPeersOnePeer() throws Exception {
        // Peer: 192.168.1.100:6881 -> 192, 168, 1, 100, 0x1A, 0xE1
        byte[] data = new byte[]{(byte) 192, (byte) 168, 1, 100, 0x1A, (byte) 0xE1};
        List<PeerInfo> peers = TrackerResponseParser.parseCompactPeers(data);
        assertEquals(1, peers.size());
        PeerInfo p = peers.get(0);
        assertEquals("192.168.1.100", p.getIp());
        assertEquals(6881, p.getPort());
        assertNull(p.getPeerId());
    }
 
    @Test
    void testParseCompactPeersMultiplePeers() throws Exception {
        // Peer 1: 192.168.1.100:6881 -> 192, 168, 1, 100, 0x1A, 0xE1
        // Peer 2: 127.0.0.1:8080 -> 127, 0, 0, 1, 0x1F, 0x90
        byte[] data = new byte[]{
            (byte) 192, (byte) 168, 1, 100, 0x1A, (byte) 0xE1,
            127, 0, 0, 1, 0x1F, (byte) 0x90
        };
        List<PeerInfo> peers = TrackerResponseParser.parseCompactPeers(data);
        assertEquals(2, peers.size());
 
        PeerInfo p1 = peers.get(0);
        assertEquals("192.168.1.100", p1.getIp());
        assertEquals(6881, p1.getPort());
 
        PeerInfo p2 = peers.get(1);
        assertEquals("127.0.0.1", p2.getIp());
        assertEquals(8080, p2.getPort());
    }
 
    @Test
    void testParseCompactPeersMalformedLength() {
        // 5 bytes (should be multiple of 6)
        byte[] data = new byte[]{127, 0, 0, 1, 0x1F};
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseCompactPeers(data));
    }
 
    @Test
    void testParseCompactPeersBoundaryPorts() throws Exception {
        // Min valid port (1)
        byte[] dataMinPort = new byte[]{127, 0, 0, 1, 0, 1};
        List<PeerInfo> peersMin = TrackerResponseParser.parseCompactPeers(dataMinPort);
        assertEquals(1, peersMin.size());
        assertEquals(1, peersMin.get(0).getPort());
 
        // Max valid port (65535)
        byte[] dataMaxPort = new byte[]{127, 0, 0, 1, (byte) 255, (byte) 255};
        List<PeerInfo> peersMax = TrackerResponseParser.parseCompactPeers(dataMaxPort);
        assertEquals(1, peersMax.size());
        assertEquals(65535, peersMax.get(0).getPort());
 
        // Invalid port (0)
        byte[] dataZeroPort = new byte[]{127, 0, 0, 1, 0, 0};
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseCompactPeers(dataZeroPort));
    }
 
    @Test
    void testParseDictionaryPeersNull() {
        assertThrows(IllegalArgumentException.class, () -> TrackerResponseParser.parseDictionaryPeers(null));
    }
 
    @Test
    void testParseDictionaryPeersZeroPeers() throws Exception {
        List<PeerInfo> peers = TrackerResponseParser.parseDictionaryPeers(new java.util.ArrayList<>());
        assertNotNull(peers);
        assertTrue(peers.isEmpty());
    }
 
    @Test
    void testParseDictionaryPeersSingleAndMultiple() throws Exception {
        java.util.Map<String, Object> p1 = new java.util.HashMap<>();
        p1.put("ip", "192.168.1.10".getBytes(StandardCharsets.UTF_8));
        p1.put("port", 6881);
        p1.put("peer id", "peer_id_123456789012".getBytes(StandardCharsets.UTF_8));
 
        java.util.Map<String, Object> p2 = new java.util.HashMap<>();
        p2.put("ip", "seed.example.com".getBytes(StandardCharsets.UTF_8));
        p2.put("port", 8080);
        // p2 has no peer id (allowed)
 
        List<Object> list = java.util.Arrays.asList(p1, p2);
        List<PeerInfo> peers = TrackerResponseParser.parseDictionaryPeers(list);
 
        assertEquals(2, peers.size());
 
        PeerInfo peer1 = peers.get(0);
        assertEquals("192.168.1.10", peer1.getIp());
        assertEquals(6881, peer1.getPort());
        assertArrayEquals("peer_id_123456789012".getBytes(StandardCharsets.UTF_8), peer1.getPeerId());
 
        PeerInfo peer2 = peers.get(1);
        assertEquals("seed.example.com", peer2.getIp());
        assertEquals(8080, peer2.getPort());
        assertNull(peer2.getPeerId());
    }
 
    @Test
    void testParseDictionaryPeersIPValidation() throws Exception {
        // Valid IP/address representations
        String[] validIPs = {"127.0.0.1", "2001:db8::1", "[2001:db8::1]", "seed.example.com", "my-host"};
        for (String validIP : validIPs) {
            java.util.Map<String, Object> p = new java.util.HashMap<>();
            p.put("ip", validIP.getBytes(StandardCharsets.UTF_8));
            p.put("port", 6881);
            List<PeerInfo> res = TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p));
            assertEquals(validIP, res.get(0).getIp());
        }
 
        // Invalid IP/address representations
        String[] invalidIPs = {"", "127.0. 0.1", "127.0.0.1/24", "127.0.0.1%eth0", "invalid_char_#"};
        for (String invalidIP : invalidIPs) {
            java.util.Map<String, Object> p = new java.util.HashMap<>();
            p.put("ip", invalidIP.getBytes(StandardCharsets.UTF_8));
            p.put("port", 6881);
            assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p)));
        }
    }
 
    @Test
    void testParseDictionaryPeersPortValidation() throws Exception {
        // Port 1 is valid
        java.util.Map<String, Object> p1 = new java.util.HashMap<>();
        p1.put("ip", "127.0.0.1".getBytes(StandardCharsets.UTF_8));
        p1.put("port", 1);
        List<PeerInfo> res1 = TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p1));
        assertEquals(1, res1.get(0).getPort());
 
        // Port 65535 is valid
        java.util.Map<String, Object> p2 = new java.util.HashMap<>();
        p2.put("ip", "127.0.0.1".getBytes(StandardCharsets.UTF_8));
        p2.put("port", 65535);
        List<PeerInfo> res2 = TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p2));
        assertEquals(65535, res2.get(0).getPort());
 
        // Port 0 is invalid
        java.util.Map<String, Object> p3 = new java.util.HashMap<>();
        p3.put("ip", "127.0.0.1".getBytes(StandardCharsets.UTF_8));
        p3.put("port", 0);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p3)));
 
        // Port 65536 is invalid
        java.util.Map<String, Object> p4 = new java.util.HashMap<>();
        p4.put("ip", "127.0.0.1".getBytes(StandardCharsets.UTF_8));
        p4.put("port", 65536);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p4)));
    }
 
    @Test
    void testParseDictionaryPeersMalformedEntries() {
        // Element not a Map
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList("not-a-map")));
 
        // Missing IP
        java.util.Map<String, Object> p1 = new java.util.HashMap<>();
        p1.put("port", 6881);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p1)));
 
        // IP not a byte array
        java.util.Map<String, Object> p2 = new java.util.HashMap<>();
        p2.put("ip", 12345);
        p2.put("port", 6881);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p2)));
 
        // Missing Port
        java.util.Map<String, Object> p3 = new java.util.HashMap<>();
        p3.put("ip", "127.0.0.1".getBytes(StandardCharsets.UTF_8));
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p3)));
 
        // Port not a Number
        java.util.Map<String, Object> p4 = new java.util.HashMap<>();
        p4.put("ip", "127.0.0.1".getBytes(StandardCharsets.UTF_8));
        p4.put("port", "6881");
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p4)));
 
        // Peer ID not a byte array
        java.util.Map<String, Object> p5 = new java.util.HashMap<>();
        p5.put("ip", "127.0.0.1".getBytes(StandardCharsets.UTF_8));
        p5.put("port", 6881);
        p5.put("peer id", 12345);
        assertThrows(TrackerException.class, () -> TrackerResponseParser.parseDictionaryPeers(java.util.Collections.singletonList(p5)));
    }
}
