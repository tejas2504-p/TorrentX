package com.torrentx.tracker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Default HTTP connector implementation using java.net.http.HttpClient.
 */
public class DefaultHttpConnector implements HttpConnector {

    private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final HttpClient httpClient;

    /**
     * Constructs a DefaultHttpConnector using the shared HttpClient instance.
     */
    public DefaultHttpConnector() {
        this(SHARED_CLIENT);
    }

    /**
     * Constructs a DefaultHttpConnector using a custom HttpClient instance.
     *
     * @param httpClient the custom HttpClient to use.
     */
    public DefaultHttpConnector(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClient cannot be null");
        }
        this.httpClient = httpClient;
    }

    @Override
    public byte[] get(String url, int timeoutMs, String userAgent) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", userAgent)
                .GET()
                .build();

        HttpResponse<byte[]> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

        int statusCode = httpResponse.statusCode();
        if (statusCode != 200) {
            throw new TrackerHttpException(statusCode, "HTTP error status code: " + statusCode);
        }

        return httpResponse.body();
    }
}
