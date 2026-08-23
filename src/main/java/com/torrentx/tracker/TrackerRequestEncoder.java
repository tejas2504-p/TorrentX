package com.torrentx.tracker;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility responsible for URL/query encoding tracker requests.
 * Standard URLEncoder doesn't handle raw binary info_hash/peer_id bytes correctly.
 */
public class TrackerRequestEncoder {

    /**
     * Encodes a TrackerRequest into a complete announce URI query string.
     *
     * @param baseAnnounceUrl the base tracker URL.
     * @param request the tracker request parameters.
     * @return the fully encoded URL with query parameters.
     */
    public static String encode(String baseAnnounceUrl, TrackerRequest request) {
        if (baseAnnounceUrl == null) {
            throw new IllegalArgumentException("Base announce URL cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Tracker request cannot be null");
        }

        StringBuilder urlBuilder = new StringBuilder(baseAnnounceUrl);
        urlBuilder.append(baseAnnounceUrl.contains("?") ? "&" : "?");

        urlBuilder.append("info_hash=").append(percentEncode(request.getInfoHash()));
        urlBuilder.append("&peer_id=").append(percentEncode(request.getPeerId()));
        urlBuilder.append("&port=").append(request.getPort());
        urlBuilder.append("&uploaded=").append(request.getUploaded());
        urlBuilder.append("&downloaded=").append(request.getDownloaded());
        urlBuilder.append("&left=").append(request.getLeft());
        urlBuilder.append("&compact=").append(request.isCompact() ? "1" : "0");

        if (request.getEvent() != null && !request.getEvent().trim().isEmpty()) {
            urlBuilder.append("&event=").append(URLEncoder.encode(request.getEvent().trim(), StandardCharsets.UTF_8));
        }

        return urlBuilder.toString();
    }

    /**
     * Percent-encodes a byte array according to RFC 3986.
     * Unreserved characters (A-Za-z0-9-_.~) are left as-is, all other bytes are escaped as %XX.
     *
     * @param bytes the raw bytes to encode.
     * @return the percent-encoded string.
     */
    public static String percentEncode(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
                    || b == '-' || b == '_' || b == '.' || b == '~') {
                sb.append((char) b);
            } else {
                sb.append(String.format("%%%02X", b));
            }
        }
        return sb.toString();
    }
}
