package com.torrentx.tracker;

import com.torrentx.torrent.BencodeDecoder;
import com.torrentx.torrent.BencodeException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parsers bencoded response bytes from a tracker.
 */
public class TrackerResponseParser {

    /**
     * Parses the bencoded bytes from a tracker.
     *
     * @param data the raw bencoded response body.
     * @return the parsed TrackerResponse.
     * @throws TrackerException if the data is malformed or invalid.
     */
    @SuppressWarnings("unchecked")
    public static TrackerResponse parse(byte[] data) throws TrackerException {
        if (data == null) {
            throw new IllegalArgumentException("Response data cannot be null");
        }

        BencodeDecoder decoder = new BencodeDecoder(data);
        Object decoded;
        try {
            decoded = decoder.decode();
        } catch (BencodeException e) {
            throw new TrackerException("Failed to decode bencoded response", e);
        }

        if (!(decoded instanceof Map)) {
            throw new TrackerException("Tracker response root is not a dictionary");
        }

        Map<String, Object> responseMap = (Map<String, Object>) decoded;

        // Check for failure reason
        if (responseMap.containsKey("failure reason")) {
            Object reasonObj = responseMap.get("failure reason");
            if (reasonObj instanceof byte[]) {
                String failureReason = new String((byte[]) reasonObj, StandardCharsets.UTF_8);
                return new TrackerResponse.Builder().failureReason(failureReason).build();
            }
            throw new TrackerException("Tracker response contains a non-string 'failure reason'");
        }

        TrackerResponse.Builder builder = new TrackerResponse.Builder();

        // Warning message
        if (responseMap.containsKey("warning message")) {
            Object warnObj = responseMap.get("warning message");
            if (warnObj instanceof byte[]) {
                builder.warningMessage(new String((byte[]) warnObj, StandardCharsets.UTF_8));
            }
        }

        // Interval (required for success, but let's check)
        if (responseMap.containsKey("interval")) {
            Object intervalObj = responseMap.get("interval");
            if (intervalObj instanceof Number) {
                builder.interval(((Number) intervalObj).intValue());
            }
        }

        // Min Interval
        if (responseMap.containsKey("min interval")) {
            Object minIntervalObj = responseMap.get("min interval");
            if (minIntervalObj instanceof Number) {
                builder.minInterval(((Number) minIntervalObj).intValue());
            }
        }

        // Tracker ID
        if (responseMap.containsKey("tracker id")) {
            Object trackerIdObj = responseMap.get("tracker id");
            if (trackerIdObj instanceof byte[]) {
                builder.trackerId(new String((byte[]) trackerIdObj, StandardCharsets.UTF_8));
            }
        }

        // Complete (seeders)
        if (responseMap.containsKey("complete")) {
            Object completeObj = responseMap.get("complete");
            if (completeObj instanceof Number) {
                builder.complete(((Number) completeObj).intValue());
            }
        }

        // Incomplete (leechers)
        if (responseMap.containsKey("incomplete")) {
            Object incompleteObj = responseMap.get("incomplete");
            if (incompleteObj instanceof Number) {
                builder.incomplete(((Number) incompleteObj).intValue());
            }
        }

        // Peers parsing
        if (responseMap.containsKey("peers")) {
            Object peersObj = responseMap.get("peers");
            List<PeerInfo> peerList = new ArrayList<>();
            if (peersObj instanceof byte[]) {
                // Compact format
                peerList = parseCompactPeers((byte[]) peersObj);
            } else if (peersObj instanceof List) {
                // Non-compact format
                peerList = parseDictionaryPeers((List<Object>) peersObj);
            } else {
                throw new TrackerException("Invalid 'peers' field format: must be a byte array or list");
            }
            builder.peers(peerList);
        }

        return builder.build();
    }

    private static List<PeerInfo> parseCompactPeers(byte[] compactPeers) throws TrackerException {
        if (compactPeers.length % 6 != 0) {
            throw new TrackerException("Compact peer list length must be a multiple of 6: " + compactPeers.length);
        }

        List<PeerInfo> peerList = new ArrayList<>();
        byte[] ipBytes = new byte[4];

        for (int i = 0; i < compactPeers.length; i += 6) {
            System.arraycopy(compactPeers, i, ipBytes, 0, 4);
            int port = ((compactPeers[i + 4] & 0xFF) << 8) | (compactPeers[i + 5] & 0xFF);

            try {
                String ip = InetAddress.getByAddress(ipBytes).getHostAddress();
                peerList.add(new PeerInfo(ip, port, null));
            } catch (UnknownHostException e) {
                throw new TrackerException("Failed to resolve IP from compact peer list", e);
            }
        }

        return peerList;
    }

    @SuppressWarnings("unchecked")
    private static List<PeerInfo> parseDictionaryPeers(List<Object> peerList) throws TrackerException {
        List<PeerInfo> parsedPeers = new ArrayList<>();

        for (Object obj : peerList) {
            if (!(obj instanceof Map)) {
                throw new TrackerException("Peer entry in non-compact list is not a dictionary");
            }

            Map<String, Object> peerMap = (Map<String, Object>) obj;

            // Extract IP
            Object ipObj = peerMap.get("ip");
            if (!(ipObj instanceof byte[])) {
                throw new TrackerException("Missing or invalid 'ip' in peer dictionary");
            }
            String ip = new String((byte[]) ipObj, StandardCharsets.UTF_8);

            // Extract Port
            Object portObj = peerMap.get("port");
            if (!(portObj instanceof Number)) {
                throw new TrackerException("Missing or invalid 'port' in peer dictionary");
            }
            int port = ((Number) portObj).intValue();

            // Extract Peer ID (optional)
            byte[] peerId = null;
            Object peerIdObj = peerMap.get("peer id");
            if (peerIdObj instanceof byte[]) {
                peerId = (byte[]) peerIdObj;
            }

            parsedPeers.add(new PeerInfo(ip, port, peerId));
        }

        return parsedPeers;
    }
}
