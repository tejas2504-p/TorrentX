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

    private final HttpClient httpClient;

    public DefaultHttpConnector() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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
