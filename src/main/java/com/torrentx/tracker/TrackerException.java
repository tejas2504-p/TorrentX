package com.torrentx.tracker;

/**
 * Exception thrown when tracker announce request or response parsing fails.
 */
public class TrackerException extends Exception {

    public TrackerException(String message) {
        super(message);
    }

    public TrackerException(String message, Throwable cause) {
        super(message, cause);
    }
}
