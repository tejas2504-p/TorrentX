package com.torrentx.tracker;
 
import com.torrentx.torrent.TorrentMetadata;
import com.torrentx.torrent.TorrentParser;
import com.torrentx.utils.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
 
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
 
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
 
class TrackerIntegrationTest {
 
    private static TorrentMetadata torrentMetadata;
 
    @BeforeAll
    static void setUp() throws Exception {
        // Ensure fixtures exist
        String singleBencode = "d8:announce35:http://tracker.example.com/announce4:infod6:lengthi12345e4:name10:single.txt12:piece lengthi16384e6:pieces20:12345678901234567890ee";
        Path fixturesDir = Paths.get("src", "test", "resources", "fixtures");
        Files.createDirectories(fixturesDir);
        Path torrentFile = fixturesDir.resolve("single_file.torrent");
        Files.write(torrentFile, singleBencode.getBytes(StandardCharsets.UTF_8));
 
        TorrentParser parser = new TorrentParser();
        torrentMetadata = parser.parse(torrentFile.toFile());
    }
 
    @Test
    void testIntegrationSuccessfulAnnounce() throws Exception {
        // 1. Successful announce with simple compact peers
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("d8:intervali1800e5:peers6:".getBytes(StandardCharsets.ISO_8859_1));
        bos.write(new byte[]{127, 0, 0, 1, 0x1A, (byte) 0xE1}); // 127.0.0.1:6881
        bos.write("e".getBytes(StandardCharsets.ISO_8859_1));
        
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(bos.toByteArray());
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(torrentMetadata.getInfoHash())
                .peerId(TrackerClient.generatePeerId())
                .build();
 
        TrackerResponse response = client.announce(torrentMetadata.getAnnounce(), request);
 
        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertEquals(1800, response.getInterval());
        assertEquals(1, response.getPeers().size());
        assertEquals("127.0.0.1", response.getPeers().get(0).getIp());
        assertEquals(6881, response.getPeers().get(0).getPort());
    }
 
    @Test
    void testIntegrationMultiplePeers() throws Exception {
        // 2. Successful announce returning multiple peers in compact format
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("d8:intervali900e5:peers12:".getBytes(StandardCharsets.ISO_8859_1));
        bos.write(new byte[]{(byte) 192, (byte) 168, 1, 100, 0x1A, (byte) 0xE1}); // 192.168.1.100:6881
        bos.write(new byte[]{10, 0, 0, 1, 0x1F, (byte) 0x90});                   // 10.0.0.1:8080
        bos.write("e".getBytes(StandardCharsets.ISO_8859_1));
 
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(bos.toByteArray());
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(torrentMetadata.getInfoHash())
                .peerId(TrackerClient.generatePeerId())
                .build();
 
        TrackerResponse response = client.announce(torrentMetadata.getAnnounce(), request);
 
        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertEquals(2, response.getPeers().size());
        assertEquals("192.168.1.100", response.getPeers().get(0).getIp());
        assertEquals(6881, response.getPeers().get(0).getPort());
        assertEquals("10.0.0.1", response.getPeers().get(1).getIp());
        assertEquals(8080, response.getPeers().get(1).getPort());
    }
 
    @Test
    void testIntegrationCompactPeers() throws Exception {
        // 3. Compact peers parsing validation
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("d8:intervali1800e5:peers6:".getBytes(StandardCharsets.ISO_8859_1));
        bos.write(new byte[]{127, 0, 0, 1, 0, 80}); // 127.0.0.1:80
        bos.write("e".getBytes(StandardCharsets.ISO_8859_1));
        
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(bos.toByteArray());
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(torrentMetadata.getInfoHash())
                .peerId(TrackerClient.generatePeerId())
                .build();
 
        TrackerResponse response = client.announce(torrentMetadata.getAnnounce(), request);
 
        assertNotNull(response);
        assertEquals(1, response.getPeers().size());
        assertEquals("127.0.0.1", response.getPeers().get(0).getIp());
        assertEquals(80, response.getPeers().get(0).getPort());
    }
 
    @Test
    void testIntegrationNonCompactPeers() throws Exception {
        // 4. Non-compact peers (dictionary list) parsing
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        
        String bencodeResponse = "d8:intervali1200e5:peersld2:ip9:127.0.0.17:peer id20:-TX1000-abcdefghijkl4:porti6881eeee";
        
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(bencodeResponse.getBytes(StandardCharsets.ISO_8859_1));
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(torrentMetadata.getInfoHash())
                .peerId(TrackerClient.generatePeerId())
                .build();
 
        TrackerResponse response = client.announce(torrentMetadata.getAnnounce(), request);
 
        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertEquals(1200, response.getInterval());
        assertEquals(1, response.getPeers().size());
        PeerInfo peer = response.getPeers().get(0);
        assertEquals("127.0.0.1", peer.getIp());
        assertEquals(6881, peer.getPort());
        assertNotNull(peer.getPeerId());
        assertEquals("-TX1000-abcdefghijkl", new String(peer.getPeerId(), StandardCharsets.US_ASCII));
    }
 
    @Test
    void testIntegrationTrackerFailureResponse() throws Exception {
        // 5. Tracker application failure response
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        
        String bencodeResponse = "d14:failure reason15:info_hash_errore";
        
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(bencodeResponse.getBytes(StandardCharsets.ISO_8859_1));
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(torrentMetadata.getInfoHash())
                .peerId(TrackerClient.generatePeerId())
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce(torrentMetadata.getAnnounce(), request));
        
        assertTrue(exception.getMessage().contains("Tracker failure: info_hash_error"));
    }
 
    @Test
    void testIntegrationMalformedResponse() throws Exception {
        // 6. Malformed response (Bencode error)
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        
        // Missing dictionary closing 'e'
        String bencodeResponse = "d8:intervali1800e5:peers0:";
        
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(bencodeResponse.getBytes(StandardCharsets.ISO_8859_1));
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(torrentMetadata.getInfoHash())
                .peerId(TrackerClient.generatePeerId())
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce(torrentMetadata.getAnnounce(), request));
        
        assertTrue(exception.getMessage().contains("Failed to decode bencoded response"));
    }
 
    @Test
    void testIntegrationHttpFailure() throws Exception {
        // 7. HTTP failures (non-200 response, like 500 server error)
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new TrackerHttpException(500, "HTTP error status code: 500"));
 
        Config config = new Config() {
            @Override
            public int getMaxRetries() { return 1; }
            @Override
            public int getInitialRetryDelayMs() { return 1; }
        };
 
        TrackerClient client = new TrackerClient(config, mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(torrentMetadata.getInfoHash())
                .peerId(TrackerClient.generatePeerId())
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce(torrentMetadata.getAnnounce(), request));
        
        assertTrue(exception.getMessage().contains("HTTP server error: 500"));
        // 1 initial + 1 retry = 2 attempts
        verify(mockHttpConnector, times(2)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testIntegrationTimeoutBehavior() throws Exception {
        // 8. Timeout behavior (HttpTimeoutException)
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new HttpTimeoutException("Read timed out"));
 
        Config config = new Config() {
            @Override
            public int getMaxRetries() { return 1; }
            @Override
            public int getInitialRetryDelayMs() { return 1; }
        };
 
        TrackerClient client = new TrackerClient(config, mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(torrentMetadata.getInfoHash())
                .peerId(TrackerClient.generatePeerId())
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce(torrentMetadata.getAnnounce(), request));
        
        assertTrue(exception.getMessage().contains("timed out"));
        // 1 initial + 1 retry = 2 attempts
        verify(mockHttpConnector, times(2)).get(anyString(), anyInt(), anyString());
    }
}
