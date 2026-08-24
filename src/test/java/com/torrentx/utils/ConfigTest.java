package com.torrentx.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    private Config config;

    @BeforeEach
    void setUp() {
        config = new Config();
    }

    @Test
    void testDefaultValues() {
        assertEquals("TorrentX", config.getClientName());
        assertEquals("1.0.0", config.getClientVersion());
        assertEquals("INFO", config.getLoggingLevel());
        assertEquals(50, config.getMaxConnections());
        assertEquals(5000, config.getConnectionTimeout());
        assertEquals(3, config.getMaxRetries());
        assertNotNull(config.getDownloadDirectory());
    }

    @Test
    void testLoadDefaultConfig() {
        config.loadDefault();
        assertEquals("TorrentX", config.getClientName());
        assertEquals("1.0.0", config.getClientVersion());
        assertEquals("INFO", config.getLoggingLevel());
        assertEquals(50, config.getMaxConnections());
        assertEquals(5000, config.getConnectionTimeout());
        assertEquals(3, config.getMaxRetries());
    }

    @Test
    void testLoadValidProperties() throws IOException {
        String data = "client.name=TorrentX-Pro\n" +
                      "client.version=2.1.0\n" +
                      "download.directory=MyTorrents\n" +
                      "max.connections=100\n" +
                      "connection.timeout=3000\n" +
                      "tracker.max.retries=5\n" +
                      "logging.level=DEBUG\n";
        
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }

        assertEquals("TorrentX-Pro", config.getClientName());
        assertEquals("2.1.0", config.getClientVersion());
        assertEquals("DEBUG", config.getLoggingLevel());
        assertEquals(100, config.getMaxConnections());
        assertEquals(3000, config.getConnectionTimeout());
        assertEquals(5, config.getMaxRetries());
        assertTrue(config.getDownloadDirectory().getPath().endsWith("MyTorrents"));
    }

    @Test
    void testLoadMissingProperties() throws IOException {
        String data = "client.name=CustomName\n";
        
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }

        assertEquals("CustomName", config.getClientName());
        // Other fields must fall back to their default values
        assertEquals("1.0.0", config.getClientVersion());
        assertEquals("INFO", config.getLoggingLevel());
        assertEquals(50, config.getMaxConnections());
        assertEquals(5000, config.getConnectionTimeout());
        assertEquals(3, config.getMaxRetries());
    }

    @Test
    void testLoadInvalidNumericMaxConnections() throws IOException {
        // Test non-numeric string
        String data = "max.connections=invalid\n";
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }
        assertEquals(50, config.getMaxConnections(), "Should fall back to default when max.connections is non-numeric");

        // Test negative number
        data = "max.connections=-10\n";
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }
        assertEquals(50, config.getMaxConnections(), "Should fall back to default when max.connections is <= 0");

        // Test zero
        data = "max.connections=0\n";
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }
        assertEquals(50, config.getMaxConnections(), "Should fall back to default when max.connections is <= 0");
    }

    @Test
    void testLoadInvalidNumericTimeout() throws IOException {
        // Test non-numeric string
        String data = "connection.timeout=abc\n";
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }
        assertEquals(5000, config.getConnectionTimeout(), "Should fall back to default when connection.timeout is non-numeric");

        // Test negative number
        data = "connection.timeout=-500\n";
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }
        assertEquals(5000, config.getConnectionTimeout(), "Should fall back to default when connection.timeout is negative");
    }

    @Test
    void testLoadEmptyInputStream() throws IOException {
        config.load((ByteArrayInputStream) null);
        assertEquals("TorrentX", config.getClientName());
    }

    @Test
    void testLoadMissingFile() throws IOException {
        config.load((File) null);
        assertEquals("TorrentX", config.getClientName());

        File nonExistentFile = new File("this_file_does_not_exist.properties");
        config.load(nonExistentFile);
        assertEquals("TorrentX", config.getClientName());
    }
 
    @Test
    void testLoadInvalidNumericMaxRetries() throws IOException {
        // Test non-numeric string
        String data = "tracker.max.retries=abc\n";
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }
        assertEquals(3, config.getMaxRetries(), "Should fall back to default when tracker.max.retries is non-numeric");
 
        // Test negative number
        data = "tracker.max.retries=-2\n";
        try (ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
            config.load(in);
        }
        assertEquals(3, config.getMaxRetries(), "Should fall back to default when tracker.max.retries is negative");
    }
}
