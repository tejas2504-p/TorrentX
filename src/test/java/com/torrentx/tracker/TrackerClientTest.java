package com.torrentx.tracker;

import com.torrentx.utils.Config;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
    void testAnnounceHttpError() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);

        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new IOException("HTTP error status code: 400"));

        TrackerClient client = new TrackerClient(new Config(), mockHttpConnector);

        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .build();

        assertThrows(TrackerException.class, () -> client.announce("http://tracker.example.com/announce", request));
    }

    @Test
    void testAnnounceRetriesOnFailure() throws Exception {
        HttpConnector mockHttpConnector = mock(HttpConnector.class);

        // Throw IOException for all attempts
        when(mockHttpConnector.get(anyString(), anyInt(), anyString()))
                .thenThrow(new IOException("Timeout/Connection Reset"));

        // Use low timeout config
        Config lowTimeoutConfig = new Config() {
            @Override
            public int getConnectionTimeout() {
                return 100;
            }
        };

        TrackerClient client = new TrackerClient(lowTimeoutConfig, mockHttpConnector);

        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .build();

        assertThrows(TrackerException.class, () -> client.announce("http://tracker.example.com/announce", request));

        // verify that it tried exactly 3 times (the maxRetries limit)
        verify(mockHttpConnector, times(3)).get(anyString(), anyInt(), anyString());
    }
}
