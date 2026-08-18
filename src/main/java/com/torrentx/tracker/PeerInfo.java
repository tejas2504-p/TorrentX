package com.torrentx.tracker;

/**
 * Model representing connectivity details of a discovered peer.
 */
public class PeerInfo {

    private final String ip;
    private final int port;
    private final byte[] peerId;

    /**
     * Constructs a PeerInfo model.
     */
    public PeerInfo(String ip, int port, byte[] peerId) {
        this.ip = ip;
        this.port = port;
        this.peerId = peerId;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public byte[] getPeerId() {
        return peerId;
    }
}
