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
        this.maxRetries = 3; // Default retries
        this.httpConnector = httpConnector;
    }

    /**
     * Sends an announce request to the specified tracker URL and parses the response.
     * Implements a retry strategy with linear backoff for network/HTTP transients.
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
        logger.debug("Announcing to tracker: {}", fullUrl);

        String userAgent = config.getClientName() + "/" + config.getClientVersion();

        int attempt = 0;
        long backoffDelayMs = 1000;

        while (true) {
            attempt++;
            try {
                byte[] body = httpConnector.get(fullUrl, timeoutMs, userAgent);
                return TrackerResponseParser.parse(body);

            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new TrackerException("Announce request was interrupted", e);
                }

                logger.warn("Tracker announce attempt {} failed for URL {}: {}", attempt, announceUrl, e.getMessage());

                if (attempt >= maxRetries) {
                    throw new TrackerException("Announce failed after " + maxRetries + " attempts", e);
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
