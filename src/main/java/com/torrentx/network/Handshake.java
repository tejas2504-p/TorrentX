package com.torrentx.network;

public class Handshake {
    public static final String PROTOCOL_IDENTIFIER = "BitTorrent protocol";
    
    private final byte[] infoHash;
    private final byte[] peerId;

    public Handshake(byte[] infoHash, byte[] peerId) {
        if (infoHash == null || infoHash.length != 20) {
            throw new IllegalArgumentException("infoHash must be 20 bytes");
        }
        if (peerId == null || peerId.length != 20) {
            throw new IllegalArgumentException("peerId must be 20 bytes");
        }
        this.infoHash = infoHash;
        this.peerId = peerId;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public byte[] getPeerId() {
        return peerId;
    }
}
