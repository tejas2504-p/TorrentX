package com.torrentx.peer;

import java.nio.ByteBuffer;

public class PeerWireMessage {
    private final byte id;
    private final byte[] payload;

    public PeerWireMessage(byte id, byte[] payload) {
        this.id = id;
        this.payload = payload != null ? payload : new byte[0];
    }

    public PeerWireMessage(byte id) {
        this(id, null);
    }

    public byte getId() {
        return id;
    }

    public byte[] getPayload() {
        return payload;
    }

    public boolean isKeepAlive() {
        return id == -1;
    }

    public byte[] serialize() {
        if (isKeepAlive()) {
            return new byte[]{0, 0, 0, 0};
        }
        int length = 1 + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(id);
        buffer.put(payload);
        return buffer.array();
    }

    public static PeerWireMessage keepAlive() {
        return new PeerWireMessage((byte) -1, null);
    }

    public static PeerWireMessage choke() {
        return new PeerWireMessage((byte) 0);
    }

    public static PeerWireMessage unchoke() {
        return new PeerWireMessage((byte) 1);
    }

    public static PeerWireMessage interested() {
        return new PeerWireMessage((byte) 2);
    }

    public static PeerWireMessage notInterested() {
        return new PeerWireMessage((byte) 3);
    }

    public static PeerWireMessage have(int pieceIndex) {
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.putInt(pieceIndex);
        return new PeerWireMessage((byte) 4, payload.array());
    }

    public static PeerWireMessage bitfield(byte[] bitfield) {
        return new PeerWireMessage((byte) 5, bitfield);
    }

    public static PeerWireMessage request(int pieceIndex, int begin, int length) {
        ByteBuffer payload = ByteBuffer.allocate(12);
        payload.putInt(pieceIndex);
        payload.putInt(begin);
        payload.putInt(length);
        return new PeerWireMessage((byte) 6, payload.array());
    }

    public static PeerWireMessage piece(int pieceIndex, int begin, byte[] block) {
        ByteBuffer payload = ByteBuffer.allocate(8 + block.length);
        payload.putInt(pieceIndex);
        payload.putInt(begin);
        payload.put(block);
        return new PeerWireMessage((byte) 7, payload.array());
    }

    public static PeerWireMessage cancel(int pieceIndex, int begin, int length) {
        ByteBuffer payload = ByteBuffer.allocate(12);
        payload.putInt(pieceIndex);
        payload.putInt(begin);
        payload.putInt(length);
        return new PeerWireMessage((byte) 8, payload.array());
    }
}
