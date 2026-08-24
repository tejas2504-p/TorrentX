package com.torrentx.tracker;

import com.torrentx.utils.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Client responsible for executing announce requests to BitTorrent HTTP/HTTPS trackers.
 */
public class TrackerClient {

    private static final Logger logger = LoggerFactory.getLogger(TrackerClient.class);

    private final HttpConnector httpConnector;
    private final Config config;
    private final int timeoutMs;
    private final int maxRetries;
 
    /**
     * Constructs a TrackerClient using default settings.
     */
    public TrackerClient() {
        this(new Config());
    }
 
    /**
     * Constructs a TrackerClient with custom configuration.
     *
     * @param config the client configuration parameters.
     */
    public TrackerClient(Config config) {
        this(config, new DefaultHttpConnector());
    }
 
    /**
     * Package-private constructor for testing with a mocked HttpConnector.
     */
    TrackerClient(Config config, HttpConnector httpConnector) {
        this.config = config != null ? config : new Config();
        this.timeoutMs = this.config.getConnectionTimeout();
        int retries = this.config.getMaxRetries();
        if (retries < 0) {
            this.maxRetries = 0;
        } else if (retries > 10) {
            this.maxRetries = 10;
        } else {
            this.maxRetries = retries;
        }
        this.httpConnector = httpConnector;
    }
 
    /**
     * Sends an announce request to the specified tracker URL and parses the response.
     * Implements a retry strategy with linear/exponential backoff for network/HTTP transients.
     *
     * @param announceUrl the base announce URL.
     * @param request the tracker request parameters.
     * @return the parsed TrackerResponse.
     * @throws TrackerException if the announce fails after all retries or response is invalid.
     */
    public TrackerResponse announce(String announceUrl, TrackerRequest request) throws TrackerException {
        if (announceUrl == null) {
            throw new IllegalArgumentException("Announce URL cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
 
        String fullUrl = TrackerRequestEncoder.encode(announceUrl, request);
        logger.debug("Announcing to tracker: {}", announceUrl);
 
        String userAgent = config.getClientName() + "/" + config.getClientVersion();
 
        int attempt = 0;
        long backoffDelayMs = 1000;
 
        while (true) {
            attempt++;
            try {
                byte[] body = httpConnector.get(fullUrl, timeoutMs, userAgent);
                TrackerResponse response = TrackerResponseParser.parse(body);
                if (!response.isSuccessful()) {
                    throw new TrackerException("Tracker failure: " + response.getFailureReason());
                }
                return response;
 
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new TrackerException("Announce request was interrupted", e);
                }
 
                // Determine retry status and descriptive message
                boolean isRetriable = false;
                String errorMsg;
 
                if (e instanceof TrackerHttpException) {
                    int statusCode = ((TrackerHttpException) e).getStatusCode();
                    if (statusCode >= 400 && statusCode < 500) {
                        isRetriable = false;
                        errorMsg = "HTTP client error: " + statusCode;
                    } else if (statusCode >= 500 && statusCode < 600) {
                        isRetriable = true;
                        errorMsg = "HTTP server error: " + statusCode;
                    } else {
                        isRetriable = false;
                        errorMsg = "HTTP error status: " + statusCode;
                    }
                } else if (isDnsFailure(e)) {
                    isRetriable = false;
                    errorMsg = "Tracker address could not be resolved";
                } else if (isConnectionRefused(e)) {
                    isRetriable = true;
                    errorMsg = "Connection refused by tracker";
                } else if (isTimeout(e)) {
                    isRetriable = true;
                    errorMsg = "Connection or read timed out with tracker";
                } else {
                    // Other general network failures can be retried
                    isRetriable = true;
                    errorMsg = "Tracker announce network error: " + e.getMessage();
                }
 
                logger.warn("Tracker announce attempt {} failed for URL {}: {} (retriable={})", 
                        attempt, announceUrl, errorMsg, isRetriable, e);
 
                if (!isRetriable || attempt > maxRetries) {
                    throw new TrackerException(errorMsg, e);
                }
 
                try {
                    Thread.sleep(backoffDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TrackerException("Backoff sleep interrupted", ie);
                }
                backoffDelayMs *= 2; // Exponential backoff
            }
        }
    }
 
    private static boolean isDnsFailure(Throwable t) {
        if (t == null) return false;
        if (t instanceof java.net.UnknownHostException) return true;
        if (t instanceof java.nio.channels.UnresolvedAddressException) return true;
        if (t.getMessage() != null) {
            String msg = t.getMessage().toLowerCase();
            if (msg.contains("unknown host") || msg.contains("unresolved")) return true;
        }
        return isDnsFailure(t.getCause());
    }
 
    private static boolean isConnectionRefused(Throwable t) {
        if (t == null) return false;
        if (t instanceof java.net.ConnectException) return true;
        if (t.getMessage() != null) {
            String msg = t.getMessage().toLowerCase();
            if (msg.contains("connection refused") || msg.contains("connrefused")) return true;
        }
        return isConnectionRefused(t.getCause());
    }
 
    private static boolean isTimeout(Throwable t) {
        if (t == null) return false;
        if (t instanceof java.net.http.HttpTimeoutException) return true;
        if (t instanceof java.net.http.HttpConnectTimeoutException) return true;
        if (t instanceof java.net.SocketTimeoutException) return true;
        if (t.getMessage() != null) {
            String msg = t.getMessage().toLowerCase();
            if (msg.contains("timeout") || msg.contains("timed out")) return true;
        }
        return isTimeout(t.getCause());
    }
 
    /**
     * Generates a 20-byte Peer ID according to the Azureus convention.
     * Format: -TX1000- followed by 12 cryptographically secure random bytes.
     * Delegates to PeerIdGenerator to maintain a stable session ID.
     *
     * @return the generated 20-byte Peer ID.
     */
    public static byte[] generatePeerId() {
        return PeerIdGenerator.getSessionPeerId();
    }
}
