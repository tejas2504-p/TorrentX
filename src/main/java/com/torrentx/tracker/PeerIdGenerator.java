package com.torrentx.tracker;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Responsible for generating and caching a session-stable BitTorrent peer ID.
 */
public class PeerIdGenerator {

    private static final byte[] PREFIX = "-TX1000-".getBytes(StandardCharsets.US_ASCII);
    
    // Thread-safe caching of the peer ID for the current JVM run
    private static volatile byte[] sessionPeerId;

    /**
     * Retrieves the cached session-stable peer ID. Generates it if it is not already initialized.
     * Returns a cloned copy to prevent external modification of the cached state.
     *
     * @return the 20-byte session peer ID.
     */
    public static byte[] getSessionPeerId() {
        if (sessionPeerId == null) {
            synchronized (PeerIdGenerator.class) {
                if (sessionPeerId == null) {
                    sessionPeerId = generate();
                }
            }
        }
        return sessionPeerId.clone();
    }

    /**
     * Resets the session-stable peer ID. Mainly used for test environments or new connection sessions.
     */
    public static void reset() {
        synchronized (PeerIdGenerator.class) {
            sessionPeerId = null;
        }
    }

    /**
     * Generates a fresh 20-byte Peer ID.
     * Format: -TX1000- followed by 12 cryptographically secure random bytes.
     *
     * @return a new 20-byte peer ID.
     */
    public static byte[] generate() {
        byte[] peerId = new byte[20];
        System.arraycopy(PREFIX, 0, peerId, 0, PREFIX.length);

        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[12];
        secureRandom.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, peerId, PREFIX.length, randomBytes.length);

        return peerId;
    }
}
