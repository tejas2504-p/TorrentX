package com.torrentx.utils;

import java.io.File;

/**
 * Handles application-wide settings and default configurations for TorrentX.
 */
public class Config {

    private int port = 6881;
    private File downloadDirectory = new File(System.getProperty("user.home"), "Downloads/TorrentX");
    private int maxConnections = 50;

    /**
     * Initializes a default Config instance.
     */
    public Config() {
        // Default constructor
    }

    /**
     * Loads application configurations from a properties file.
     *
     * @param configFile the properties file.
     * @throws Exception if loading fails.
     */
    public void load(File configFile) throws Exception {
        // TODO: Implement configurations loading from a local file in future phases
        throw new UnsupportedOperationException("Config loading is not implemented yet.");
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public File getDownloadDirectory() {
        return downloadDirectory;
    }

    public void setDownloadDirectory(File downloadDirectory) {
        this.downloadDirectory = downloadDirectory;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
}
