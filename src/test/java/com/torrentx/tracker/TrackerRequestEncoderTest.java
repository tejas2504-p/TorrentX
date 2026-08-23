package com.torrentx.tracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackerRequestEncoderTest {

    @Test
    void testPercentEncodeUnreservedCharacters() {
        byte[] unreserved = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~".getBytes();
        String result = TrackerRequestEncoder.percentEncode(unreserved);
        assertEquals("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~", result);
    }

    @Test
    void testPercentEncodeReservedAndSpecialCharacters() {
        byte[] data = new byte[]{
                (byte) ' ', // 0x20 -> %20
                (byte) 0x00, // %00
                (byte) 0x7F, // %7F
                (byte) 0xFF, // %FF
                (byte) 0x0A, // %0A
                (byte) '/'   // 0x2F -> %2F
        };
        String result = TrackerRequestEncoder.percentEncode(data);
        assertEquals("%20%00%7F%FF%0A%2F", result);
    }

    @Test
    void testEncodeFullTrackerUrl() {
        byte[] infoHash = new byte[20];
        infoHash[0] = (byte) 0x12;
        infoHash[1] = (byte) 'A';
        infoHash[2] = (byte) 0xFF;

        byte[] peerId = new byte[20];
        peerId[0] = (byte) '-';
        peerId[1] = (byte) 'T';
        peerId[2] = (byte) 'X';
        peerId[3] = (byte) '1';
        peerId[4] = (byte) '0';
        peerId[5] = (byte) '0';
        peerId[6] = (byte) '0';
        peerId[7] = (byte) '-';
        // Fill rest with 0x01
        for (int i = 8; i < 20; i++) {
            peerId[i] = 0x01;
        }

        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .port(6882)
                .uploaded(100)
                .downloaded(50)
                .left(500)
                .compact(true)
                .event("started")
                .build();

        String baseUrl = "http://tracker.example.com/announce";
        String encodedUrl = TrackerRequestEncoder.encode(baseUrl, request);

        assertTrue(encodedUrl.startsWith(baseUrl + "?"));
        assertTrue(encodedUrl.contains("info_hash=%12A%FF%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00"));
        assertTrue(encodedUrl.contains("peer_id=-TX1000-%01%01%01%01%01%01%01%01%01%01%01%01"));
        assertTrue(encodedUrl.contains("port=6882"));
        assertTrue(encodedUrl.contains("uploaded=100"));
        assertTrue(encodedUrl.contains("downloaded=50"));
        assertTrue(encodedUrl.contains("left=500"));
        assertTrue(encodedUrl.contains("compact=1"));
        assertTrue(encodedUrl.contains("event=started"));
    }

    @Test
    void testEncodeWithExistingQueryParameters() {
        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];
        TrackerRequest request = new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .build();

        String baseUrl = "http://tracker.example.com/announce?auth=abc";
        String encodedUrl = TrackerRequestEncoder.encode(baseUrl, request);

        assertTrue(encodedUrl.startsWith("http://tracker.example.com/announce?auth=abc&info_hash="));
    }
}
