package com.torrentx.network;

/**
 * Represents a standard protocol wire message.
 */
public class Message {

    private final boolean keepAlive;
    private final byte id;
    private final byte[] payload;

    private static final Message KEEP_ALIVE_INSTANCE = new Message();

    /**
     * Constructs a Message with type ID and optional payload.
     */
    public Message(byte id, byte[] payload) {
        this.keepAlive = false;
        this.id = id;
        this.payload = payload;
    }

    private Message() {
        this.keepAlive = true;
        this.id = -1;
        this.payload = new byte[0];
    }

    public static Message keepAlive() {
        return KEEP_ALIVE_INSTANCE;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public byte getId() {
        return id;
    }

    public byte[] getPayload() {
        return payload;
    }
}
