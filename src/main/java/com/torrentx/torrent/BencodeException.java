package com.torrentx.torrent;

/**
 * Exception thrown when Bencode data cannot be parsed or decoded correctly.
 */
public class BencodeException extends Exception {
    
    public BencodeException(String message) {
        super(message);
    }

    public BencodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
