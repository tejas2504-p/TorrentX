package com.torrentx.network;

public class Handshake {
    public static final String PROTOCOL_IDENTIFIER = "BitTorrent protocol";
    
    private final byte[] infoHash;
    private final byte[] peerId;
    private final byte[] reserved;

    public Handshake(byte[] infoHash, byte[] peerId) {
        this(infoHash, peerId, new byte[8]);
    }

    public Handshake(byte[] infoHash, byte[] peerId, byte[] reserved) {
        if (infoHash == null || infoHash.length != 20) {
            throw new IllegalArgumentException("infoHash must be 20 bytes");
        }
        if (peerId == null || peerId.length != 20) {
            throw new IllegalArgumentException("peerId must be 20 bytes");
        }
        if (reserved == null || reserved.length != 8) {
            throw new IllegalArgumentException("reserved must be 8 bytes");
        }
        this.infoHash = infoHash.clone();
        this.peerId = peerId.clone();
        this.reserved = reserved.clone();
    }

    public byte[] getInfoHash() {
        return infoHash.clone();
    }

    public byte[] getPeerId() {
        return peerId.clone();
    }

    public byte[] getReserved() {
        return reserved.clone();
    }
}
