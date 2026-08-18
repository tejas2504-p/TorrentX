package com.torrentx.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Handles application-wide settings and default configurations for TorrentX.
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    private static final String DEFAULT_CLIENT_NAME = "TorrentX";
    private static final String DEFAULT_CLIENT_VERSION = "1.0.0";
    private static final String DEFAULT_LOGGING_LEVEL = "INFO";
    private static final int DEFAULT_MAX_CONNECTIONS = 50;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;

    private String clientName = DEFAULT_CLIENT_NAME;
    private String clientVersion = DEFAULT_CLIENT_VERSION;
    private File downloadDirectory;
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private String loggingLevel = DEFAULT_LOGGING_LEVEL;

    /**
     * Initializes a default Config instance with fallback settings.
     */
    public Config() {
        String userHome = System.getProperty("user.home");
        this.downloadDirectory = new File(userHome, "Downloads/TorrentX");
    }

    /**
     * Loads configurations from the default classpath location ("/config.properties").
     */
    public void loadDefault() {
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in == null) {
                logger.warn("Default config.properties not found in classpath. Using default settings.");
                return;
            }
            load(in);
        } catch (Exception e) {
            logger.error("Failed to load default config.properties. Using default settings.", e);
        }
    }

    /**
     * Loads configurations from a local properties file.
     *
     * @param configFile the properties file.
     * @throws IOException if file reading fails.
     */
    public void load(File configFile) throws IOException {
        if (configFile == null || !configFile.exists()) {
            logger.warn("Config file is missing. Using default settings.");
            return;
        }
        try (InputStream in = new FileInputStream(configFile)) {
            load(in);
        }
    }

    /**
     * Loads configurations from an input stream.
     *
     * @param inputStream the stream to load properties from.
     * @throws IOException if stream reading fails.
     */
    public void load(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            logger.warn("Input stream is null. Using default settings.");
            return;
        }
        Properties props = new Properties();
        props.load(inputStream);

        this.clientName = props.getProperty("client.name", DEFAULT_CLIENT_NAME);
        this.clientVersion = props.getProperty("client.version", DEFAULT_CLIENT_VERSION);
        this.loggingLevel = props.getProperty("logging.level", DEFAULT_LOGGING_LEVEL);

        String dirStr = props.getProperty("download.directory");
        if (dirStr != null && !dirStr.trim().isEmpty()) {
            File dir = new File(dirStr);
            if (!dir.isAbsolute()) {
                dir = new File(System.getProperty("user.home"), dirStr);
            }
            this.downloadDirectory = dir;
        }

        // Validate and parse max.connections
        String maxConnStr = props.getProperty("max.connections");
        if (maxConnStr != null) {
            try {
                int val = Integer.parseInt(maxConnStr.trim());
                if (val <= 0) {
                    logger.warn("Invalid max.connections value: {}. Must be positive. Falling back to default: {}", val, DEFAULT_MAX_CONNECTIONS);
                    this.maxConnections = DEFAULT_MAX_CONNECTIONS;
                } else {
                    this.maxConnections = val;
                }
            } catch (NumberFormatException e) {
                logger.warn("Non-numeric max.connections value: '{}'. Falling back to default: {}", maxConnStr, DEFAULT_MAX_CONNECTIONS);
                this.maxConnections = DEFAULT_MAX_CONNECTIONS;
            }
        }

        // Validate and parse connection.timeout
        String timeoutStr = props.getProperty("connection.timeout");
        if (timeoutStr != null) {
            try {
                int val = Integer.parseInt(timeoutStr.trim());
                if (val < 0) {
                    logger.warn("Invalid connection.timeout value: {}. Must be non-negative. Falling back to default: {}", val, DEFAULT_CONNECTION_TIMEOUT);
                    this.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
                } else {
                    this.connectionTimeout = val;
                }
            } catch (NumberFormatException e) {
                logger.warn("Non-numeric connection.timeout value: '{}'. Falling back to default: {}", timeoutStr, DEFAULT_CONNECTION_TIMEOUT);
                this.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
            }
        }
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public File getDownloadDirectory() {
        return downloadDirectory;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public String getLoggingLevel() {
        return loggingLevel;
    }
}
