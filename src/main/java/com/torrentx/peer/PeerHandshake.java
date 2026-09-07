package com.torrentx.peer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PeerHandshake {
    public static final String PROTOCOL_STRING = "BitTorrent protocol";
    public static final int HANDSHAKE_LENGTH = 68;

    private final byte[] infoHash;
    private final byte[] peerId;
    private final byte[] reserved;

    public PeerHandshake(byte[] infoHash, byte[] peerId) {
        this(infoHash, peerId, new byte[8]);
    }

    public PeerHandshake(byte[] infoHash, byte[] peerId, byte[] reserved) {
        if (infoHash == null || infoHash.length != 20) {
            throw new IllegalArgumentException("Info hash must be exactly 20 bytes");
        }
        if (peerId == null || peerId.length != 20) {
            throw new IllegalArgumentException("Peer ID must be exactly 20 bytes");
        }
        if (reserved == null || reserved.length != 8) {
            throw new IllegalArgumentException("Reserved bytes must be exactly 8 bytes");
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

    public ByteBuffer toByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(HANDSHAKE_LENGTH);
        byte[] pstrBytes = PROTOCOL_STRING.getBytes(StandardCharsets.UTF_8);
        buffer.put((byte) pstrBytes.length);
        buffer.put(pstrBytes);
        buffer.put(reserved);
        buffer.put(infoHash);
        buffer.put(peerId);
        buffer.flip();
        return buffer;
    }

    public static PeerHandshake parse(ByteBuffer buffer) {
        if (buffer.remaining() < HANDSHAKE_LENGTH) {
            return null; // Not enough bytes
        }
        
        buffer.mark();
        int pstrlen = buffer.get() & 0xFF;
        if (pstrlen != 19) {
            buffer.reset();
            throw new IllegalArgumentException("Invalid protocol string length: " + pstrlen);
        }

        byte[] pstrBytes = new byte[19];
        buffer.get(pstrBytes);
        String pstr = new String(pstrBytes, StandardCharsets.UTF_8);
        if (!PROTOCOL_STRING.equals(pstr)) {
            buffer.reset();
            throw new IllegalArgumentException("Unsupported protocol: " + pstr);
        }

        byte[] reserved = new byte[8];
        buffer.get(reserved);

        byte[] infoHash = new byte[20];
        buffer.get(infoHash);

        byte[] peerId = new byte[20];
        buffer.get(peerId);

        return new PeerHandshake(infoHash, peerId, reserved);
    }
}
