package com.torrentx.tracker;
 
import java.io.IOException;
 
/**
 * Exception thrown when tracker responds with a non-200 HTTP status code.
 */
public class TrackerHttpException extends IOException {
 
    private final int statusCode;
 
    public TrackerHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
 
    public int getStatusCode() {
        return statusCode;
    }
}
