package com.torrentx.tracker;

import java.io.IOException;

/**
 * Interface abstracting the HTTP network connector.
 * Allows clean unit mocking and decoupling of network calls from Tracker protocol logic.
 */
public interface HttpConnector {

    /**
     * Executes an HTTP GET request and returns the response body as a byte array.
     *
     * @param url the full URL to query.
     * @param timeoutMs connection and read timeout in milliseconds.
     * @param userAgent the User-Agent header value.
     * @return the response body bytes.
     * @throws IOException if network communication fails.
     * @throws InterruptedException if the request is interrupted.
     */
    byte[] get(String url, int timeoutMs, String userAgent) throws IOException, InterruptedException;
}
