package com.torrentx.network;

/**
 * Represents a standard protocol wire message.
 */
public class Message {

    public static final byte ID_CHOKE = 0;
    public static final byte ID_UNCHOKE = 1;
    public static final byte ID_INTERESTED = 2;
    public static final byte ID_NOT_INTERESTED = 3;
    public static final byte ID_HAVE = 4;
    public static final byte ID_BITFIELD = 5;

    private final boolean keepAlive;
    private final byte id;
    private final byte[] payload;

    private static final Message KEEP_ALIVE_INSTANCE = new Message();

    /**
     * Constructs a Message with type ID and optional payload.
     */
    public Message(byte id, byte[] payload) {
        if (payload == null) {
            payload = new byte[0];
        }
        
        switch (id) {
            case ID_CHOKE:
            case ID_UNCHOKE:
            case ID_INTERESTED:
            case ID_NOT_INTERESTED:
                if (payload.length != 0) {
                    throw new IllegalArgumentException("Message ID " + id + " must have 0-byte payload");
                }
                break;
            case ID_HAVE:
                if (payload.length != 4) {
                    throw new IllegalArgumentException("Message ID " + id + " must have 4-byte payload");
                }
                break;
            case ID_BITFIELD:
                // Bitfield length depends on piece count, validated externally by BitfieldMessage
                break;
            // Other message types will be validated in later phases
        }
        
        this.keepAlive = false;
        this.id = id;
        this.payload = payload.clone(); // Immutable copy
    }

    private Message() {
        this.keepAlive = true;
        this.id = -1;
        this.payload = new byte[0];
    }

    public static Message keepAlive() {
        return KEEP_ALIVE_INSTANCE;
    }

    public static Message choke() {
        return new Message(ID_CHOKE, new byte[0]);
    }

    public static Message unchoke() {
        return new Message(ID_UNCHOKE, new byte[0]);
    }

    public static Message interested() {
        return new Message(ID_INTERESTED, new byte[0]);
    }

    public static Message notInterested() {
        return new Message(ID_NOT_INTERESTED, new byte[0]);
    }

    public static Message have(int pieceIndex) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);
        return new Message(ID_HAVE, buffer.array());
    }

    public static Message bitfield(byte[] bitfield) {
        return new Message(ID_BITFIELD, bitfield);
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public byte getId() {
        return id;
    }

    public byte[] getPayload() {
        return payload.clone(); // Return defensive copy to maintain immutability
    }
}
