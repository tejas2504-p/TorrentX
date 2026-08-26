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
            if (!(reasonObj instanceof byte[])) {
                throw new TrackerException("Tracker response contains a non-string 'failure reason'");
            }
            String failureReason = new String((byte[]) reasonObj, StandardCharsets.UTF_8);
            
            TrackerResponse.Builder builder = new TrackerResponse.Builder().failureReason(failureReason);
            
            // Warning message (optional in failure too)
            if (responseMap.containsKey("warning message")) {
                Object warnObj = responseMap.get("warning message");
                if (!(warnObj instanceof byte[])) {
                    throw new TrackerException("Tracker response contains a non-string 'warning message'");
                }
                builder.warningMessage(new String((byte[]) warnObj, StandardCharsets.UTF_8));
            }
            
            return builder.build();
        }
 
        TrackerResponse.Builder builder = new TrackerResponse.Builder();
 
        // Warning message
        if (responseMap.containsKey("warning message")) {
            Object warnObj = responseMap.get("warning message");
            if (!(warnObj instanceof byte[])) {
                throw new TrackerException("Tracker response contains a non-string 'warning message'");
            }
            builder.warningMessage(new String((byte[]) warnObj, StandardCharsets.UTF_8));
        }
 
        // Interval (required for success)
        if (!responseMap.containsKey("interval")) {
            throw new TrackerException("Missing required field: interval");
        }
        Object intervalObj = responseMap.get("interval");
        if (!(intervalObj instanceof Number)) {
            throw new TrackerException("Invalid 'interval' field type: must be a number");
        }
        long intervalVal = ((Number) intervalObj).longValue();
        if (intervalVal < 0 || intervalVal > Integer.MAX_VALUE) {
            throw new TrackerException("Interval value out of valid integer range: " + intervalVal);
        }
        int interval = (int) intervalVal;
        builder.interval(interval);

        // Min Interval
        if (responseMap.containsKey("min interval")) {
            Object minIntervalObj = responseMap.get("min interval");
            if (!(minIntervalObj instanceof Number)) {
                throw new TrackerException("Invalid 'min interval' field type: must be a number");
            }
            long minIntervalVal = ((Number) minIntervalObj).longValue();
            if (minIntervalVal < 0 || minIntervalVal > Integer.MAX_VALUE) {
                throw new TrackerException("Min interval value out of valid integer range: " + minIntervalVal);
            }
            int minInterval = (int) minIntervalVal;
            builder.minInterval(minInterval);
        }

        // Tracker ID
        if (responseMap.containsKey("tracker id")) {
            Object trackerIdObj = responseMap.get("tracker id");
            if (!(trackerIdObj instanceof byte[])) {
                throw new TrackerException("Invalid 'tracker id' field type: must be a string (byte array)");
            }
            builder.trackerId(new String((byte[]) trackerIdObj, StandardCharsets.UTF_8));
        }

        // Complete (seeders)
        if (responseMap.containsKey("complete")) {
            Object completeObj = responseMap.get("complete");
            if (!(completeObj instanceof Number)) {
                throw new TrackerException("Invalid 'complete' field type: must be a number");
            }
            long completeVal = ((Number) completeObj).longValue();
            if (completeVal < 0 || completeVal > Integer.MAX_VALUE) {
                throw new TrackerException("Complete count out of valid integer range: " + completeVal);
            }
            int complete = (int) completeVal;
            builder.complete(complete);
        }

        // Incomplete (leechers)
        if (responseMap.containsKey("incomplete")) {
            Object incompleteObj = responseMap.get("incomplete");
            if (!(incompleteObj instanceof Number)) {
                throw new TrackerException("Invalid 'incomplete' field type: must be a number");
            }
            long incompleteVal = ((Number) incompleteObj).longValue();
            if (incompleteVal < 0 || incompleteVal > Integer.MAX_VALUE) {
                throw new TrackerException("Incomplete count out of valid integer range: " + incompleteVal);
            }
            int incomplete = (int) incompleteVal;
            builder.incomplete(incomplete);
        }
 
        // Peers parsing (required)
        if (!responseMap.containsKey("peers")) {
            throw new TrackerException("Missing required field: peers");
        }
        Object peersObj = responseMap.get("peers");
        List<PeerInfo> peerList;
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
 
        return builder.build();
    }
 
    public static List<PeerInfo> parseCompactPeers(byte[] compactPeers) throws TrackerException {
        if (compactPeers == null) {
            throw new IllegalArgumentException("Compact peers data cannot be null");
        }
        if (compactPeers.length % 6 != 0) {
            throw new TrackerException("Compact peer list length must be a multiple of 6: " + compactPeers.length);
        }
 
        List<PeerInfo> peerList = new ArrayList<>();
 
        for (int i = 0; i < compactPeers.length; i += 6) {
            byte[] ipBytes = new byte[4];
            System.arraycopy(compactPeers, i, ipBytes, 0, 4);
            int port = ((compactPeers[i + 4] & 0xFF) << 8) | (compactPeers[i + 5] & 0xFF);
            if (port < 1 || port > 65535) {
                throw new TrackerException("Invalid port number in compact peers: " + port);
            }
 
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
    public static List<PeerInfo> parseDictionaryPeers(List<Object> peerList) throws TrackerException {
        if (peerList == null) {
            throw new IllegalArgumentException("Peer list cannot be null");
        }
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
            if (ip.isEmpty()) {
                throw new TrackerException("Empty 'ip' in peer dictionary");
            }
            // Basic character validation for hostname/IP address representation
            for (int k = 0; k < ip.length(); k++) {
                char ch = ip.charAt(k);
                if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') 
                        || ch == '.' || ch == ':' || ch == '-' || ch == '[' || ch == ']')) {
                    throw new TrackerException("Invalid character in IP/hostname: " + ch);
                }
            }
 
            // Extract Port
            Object portObj = peerMap.get("port");
            if (!(portObj instanceof Number)) {
                throw new TrackerException("Missing or invalid 'port' in peer dictionary");
            }
            long portVal = ((Number) portObj).longValue();
            if (portVal < 1 || portVal > 65535) {
                throw new TrackerException("Invalid port number in peer dictionary: " + portVal);
            }
            int port = (int) portVal;
 
            // Extract Peer ID (optional)
            byte[] peerId = null;
            Object peerIdObj = peerMap.get("peer id");
            if (peerIdObj != null) {
                if (!(peerIdObj instanceof byte[])) {
                    throw new TrackerException("Invalid 'peer id' type in peer dictionary: must be a byte array");
                }
                peerId = (byte[]) peerIdObj;
            }
 
            parsedPeers.add(new PeerInfo(ip, port, peerId));
        }
 
        return parsedPeers;
    }
}
