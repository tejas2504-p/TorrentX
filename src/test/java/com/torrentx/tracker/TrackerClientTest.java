package com.torrentx.tracker;
 
import com.torrentx.utils.Config;
import org.junit.jupiter.api.Test;
 
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
 
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
 
class TrackerClientTest {
 
    @Test
    void testGeneratePeerIdLengthAndPrefix() {
        byte[] peerId = TrackerClient.generatePeerId();
        assertNotNull(peerId);
        assertEquals(20, peerId.length);
        String prefix = new String(peerId, 0, 8, StandardCharsets.US_ASCII);
        assertEquals("-TX1000-", prefix);
    }
 
    @Test
    void testAnnounceSuccess() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        String bencodeResponse = "d8:intervali1800e5:peers0:e";
        byte[] responseBytes = bencodeResponse.getBytes(StandardCharsets.ISO_8859_1);
 
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(responseBytes);
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
 
        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .build();
 
        TrackerResponse response = client.announce("http://tracker.example.com/announce", request);
 
        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertEquals(1800, response.getInterval());
        assertTrue(response.getPeers().isEmpty());
 
        verify(mockHttpConnector, times(1)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testAnnounceDnsFailure() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new UnknownHostException("tracker.example.com"));
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
 
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce("http://tracker.example.com/announce", request));
        
        assertTrue(exception.getMessage().contains("could not be resolved"));
        // DNS failure is non-transient, should only be attempted once
        verify(mockHttpConnector, times(1)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testAnnounceConnectionRefused() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new ConnectException("Connection refused"));
 
        // Mock Config for fast retry
        Config config = new Config() {
            @Override
            public int getMaxRetries() { return 3; }
            @Override
            public int getInitialRetryDelayMs() { return 1; }
        };
 
        TrackerClient client = new TrackerClient(config, mockHttpConnector);
 
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce("http://tracker.example.com/announce", request));
        
        assertTrue(exception.getMessage().contains("refused by tracker"));
        // 1 initial attempt + 3 retries = 4 times
        verify(mockHttpConnector, times(4)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testAnnounceTimeout() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new HttpTimeoutException("Request timed out"));
 
        Config config = new Config() {
            @Override
            public int getMaxRetries() { return 2; }
            @Override
            public int getInitialRetryDelayMs() { return 1; }
        };
 
        TrackerClient client = new TrackerClient(config, mockHttpConnector);
 
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce("http://tracker.example.com/announce", request));
        
        assertTrue(exception.getMessage().contains("timed out"));
        // 1 initial attempt + 2 retries = 3 times
        verify(mockHttpConnector, times(3)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testAnnounceHttpClientError4xx() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new TrackerHttpException(400, "HTTP error status code: 400"));
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
 
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce("http://tracker.example.com/announce", request));
        
        assertTrue(exception.getMessage().contains("HTTP client error: 400"));
        // Client errors (4xx) are non-retriable, should only attempt once
        verify(mockHttpConnector, times(1)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testAnnounceHttpServerError5xx() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new TrackerHttpException(503, "HTTP error status code: 503"));
 
        Config config = new Config() {
            @Override
            public int getMaxRetries() { return 3; }
            @Override
            public int getInitialRetryDelayMs() { return 1; }
        };
 
        TrackerClient client = new TrackerClient(config, mockHttpConnector);
 
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce("http://tracker.example.com/announce", request));
        
        assertTrue(exception.getMessage().contains("HTTP server error: 503"));
        // Server errors (5xx) are retriable
        verify(mockHttpConnector, times(4)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testAnnounceMalformedBencode() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        // Missing the closing 'e'
        byte[] malformedResponse = "d8:intervali1800e5:peers0:".getBytes(StandardCharsets.ISO_8859_1);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(malformedResponse);
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
 
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce("http://tracker.example.com/announce", request));
        
        assertTrue(exception.getMessage().contains("Failed to decode bencoded response"));
        // Decoding failure is non-retriable
        verify(mockHttpConnector, times(1)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testAnnounceTrackerFailureReason() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        String failureBencode = "d14:failure reason15:invalid requeste";
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenReturn(failureBencode.getBytes(StandardCharsets.ISO_8859_1));
 
        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
 
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();
 
        TrackerException exception = assertThrows(TrackerException.class, 
                () -> client.announce("http://tracker.example.com/announce", request));
        
        assertTrue(exception.getMessage().contains("Tracker failure: invalid request"));
        // Tracker application failure is non-retriable
        verify(mockHttpConnector, times(1)).get(anyString(), anyInt(), anyString());
    }
 
    @Test
    void testAnnounceSuccessOnRetry() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        String bencodeResponse = "d8:intervali1800e5:peers0:e";
        byte[] successBytes = bencodeResponse.getBytes(StandardCharsets.ISO_8859_1);
 
        // Fail on first attempt, succeed on second attempt
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new ConnectException("Temporary connection issue"))
                .thenReturn(successBytes);
 
        Config config = new Config() {
            @Override
            public int getMaxRetries() { return 3; }
            @Override
            public int getInitialRetryDelayMs() { return 1; }
        };
 
        TrackerClient client = new TrackerClient(config, mockHttpConnector);
 
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();
 
        TrackerResponse response = client.announce("http://tracker.example.com/announce", request);

        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertEquals(1800, response.getInterval());
        verify(mockHttpConnector, times(2)).get(anyString(), anyInt(), anyString());
    }

    @Test
    void testAnnounceMissingPeers() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        // Missing "peers" key
        byte[] missingPeersResponse = "d8:intervali1800ee".getBytes(StandardCharsets.ISO_8859_1);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString())).thenReturn(missingPeersResponse);

        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();

        TrackerException exception = assertThrows(TrackerException.class,
                () -> client.announce("http://tracker.example.com/announce", request));
        assertTrue(exception.getMessage().contains("Missing required field: peers"));
    }

    @Test
    void testAnnounceInvalidPeerData() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        // Invalid compact peers format (length not multiple of 6, e.g. 5 bytes)
        byte[] badPeersResponse = "d8:intervali1800e5:peers5:12345e".getBytes(StandardCharsets.ISO_8859_1);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString())).thenReturn(badPeersResponse);

        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();

        TrackerException exception = assertThrows(TrackerException.class,
                () -> client.announce("http://tracker.example.com/announce", request));
        assertTrue(exception.getMessage().contains("Compact peer list length must be a multiple of 6"));
    }

    @Test
    void testAnnounceInvalidInterval() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        // Invalid interval type (string instead of integer)
        byte[] badIntervalResponse = "d8:interval5:1800e5:peers0:e".getBytes(StandardCharsets.ISO_8859_1);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString())).thenReturn(badIntervalResponse);

        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();

        TrackerException exception = assertThrows(TrackerException.class,
                () -> client.announce("http://tracker.example.com/announce", request));
        assertTrue(exception.getMessage().contains("Invalid 'interval' field type"));
    }

    @Test
    void testAnnounceInvalidResponseStructure() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);
        // Response root is a bencoded list instead of a dictionary
        byte[] listResponse = "li1800ee".getBytes(StandardCharsets.ISO_8859_1);
        when(mockHttpConnector.get(anyString(), anyInt(), anyString())).thenReturn(listResponse);

        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(new byte[20])
                .peerId(new byte[20])
                .build();

        TrackerException exception = assertThrows(TrackerException.class,
                () -> client.announce("http://tracker.example.com/announce", request));
        assertTrue(exception.getMessage().contains("Tracker response root is not a dictionary"));
    }
}
