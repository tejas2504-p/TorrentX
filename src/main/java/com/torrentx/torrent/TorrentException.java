package com.torrentx.torrent;

/**
 * Exception thrown when torrent metadata parsing or validation fails.
 */
public class TorrentException extends Exception {
    public TorrentException(String message) {
        super(message);
    }

    public TorrentException(String message, Throwable cause) {
        super(message, cause);
    }
}