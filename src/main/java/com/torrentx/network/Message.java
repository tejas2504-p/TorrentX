package com.torrentx.network;

/**
 * Represents a standard protocol wire message.
 */
public class Message {

    private final byte id;
    private final byte[] payload;

    /**
     * Constructs a Message with type ID and optional payload.
     */
    public Message(byte id, byte[] payload) {
        this.id = id;
        this.payload = payload;
    }

    public byte getId() {
        return id;
    }

    public byte[] getPayload() {
        return payload;
    }
}
