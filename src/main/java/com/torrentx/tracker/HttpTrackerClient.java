package com.torrentx.tracker;

import com.torrentx.bencode.BencodeDecoder;
import com.torrentx.bencode.BencodeException;
import com.torrentx.bencode.Metainfo;
import com.torrentx.peer.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpTrackerClient implements TrackerClient {
    private static final Logger logger = LoggerFactory.getLogger(HttpTrackerClient.class);
    private final HttpClient httpClient;

    public HttpTrackerClient() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public List<Peer> announce(Metainfo metainfo, byte[] peerId, int port, long uploaded, long downloaded, long left, String event) throws Exception {
        String infoHashEncoded = urlEncodeBytes(metainfo.getInfoHash());
        String peerIdEncoded = urlEncodeBytes(peerId);

        StringBuilder urlBuilder = new StringBuilder(metainfo.getAnnounce());
        urlBuilder.append("?")
                .append("info_hash=").append(infoHashEncoded)
                .append("&peer_id=").append(peerIdEncoded)
                .append("&port=").append(port)
                .append("&uploaded=").append(uploaded)
                .append("&downloaded=").append(downloaded)
                .append("&left=").append(left)
                .append("&compact=1");

        if (event != null && !event.isEmpty()) {
            urlBuilder.append("&event=").append(event);
        }

        String trackerUrl = urlBuilder.toString();
        logger.info("Sending HTTP tracker announce request: {}", trackerUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trackerUrl))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Tracker returned status code " + response.statusCode());
        }

        BencodeDecoder decoder = new BencodeDecoder(response.body());
        Object decodedResponse = decoder.decode();

        if (!(decodedResponse instanceof Map)) {
            throw new BencodeException("Invalid tracker response format: expected dictionary");
        }

        Map<?, ?> responseMap = (Map<?, ?>) decodedResponse;

        if (responseMap.containsKey("failure reason")) {
            byte[] reasonBytes = (byte[]) responseMap.get("failure reason");
            String reason = new String(reasonBytes, java.nio.charset.StandardCharsets.UTF_8);
            throw new IOException("Tracker announce failed: " + reason);
        }

        List<Peer> peers = new ArrayList<>();
        Object peersObj = responseMap.get("peers");

        if (peersObj instanceof byte[]) {
            byte[] peersBytes = (byte[]) peersObj;
            if (peersBytes.length % 6 != 0) {
                throw new BencodeException("Compact peers list must be a multiple of 6 bytes");
            }
            int count = peersBytes.length / 6;
            for (int i = 0; i < count; i++) {
                int offset = i * 6;
                String ip = String.format("%d.%d.%d.%d",
                        peersBytes[offset] & 0xFF,
                        peersBytes[offset + 1] & 0xFF,
                        peersBytes[offset + 2] & 0xFF,
                        peersBytes[offset + 3] & 0xFF);
                int peerPort = ((peersBytes[offset + 4] & 0xFF) << 8) | (peersBytes[offset + 5] & 0xFF);
                peers.add(new Peer("", ip, peerPort));
            }
        } else if (peersObj instanceof List) {
            for (Object peerEntry : (List<?>) peersObj) {
                if (peerEntry instanceof Map) {
                    Map<?, ?> peerMap = (Map<?, ?>) peerEntry;
                    byte[] ipBytes = (byte[]) peerMap.get("ip");
                    String ip = new String(ipBytes, java.nio.charset.StandardCharsets.UTF_8);
                    
                    Long peerPort = (Long) peerMap.get("port");
                    
                    byte[] peerIdBytes = (byte[]) peerMap.get("peer id");
                    String pId = peerIdBytes != null ? new String(peerIdBytes, java.nio.charset.StandardCharsets.UTF_8) : "";
                    
                    peers.add(new Peer(pId, ip, peerPort.intValue()));
                }
            }
        }

        logger.info("Found {} peers from HTTP tracker", peers.size());
        return peers;
    }

    private String urlEncodeBytes(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') ||
                b == '-' || b == '_' || b == '.' || b == '~') {
                sb.append((char) b);
            } else {
                sb.append(String.format("%%%02X", b));
            }
        }
        return sb.toString();
    }
}
