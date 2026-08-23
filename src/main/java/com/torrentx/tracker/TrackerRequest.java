package com.torrentx.tracker;

import java.util.Arrays;

/**
 * Model representing parameters sent in a tracker announce request.
 */
public class TrackerRequest {

    private final byte[] infoHash;
    private final byte[] peerId;
    private final int port;
    private final long uploaded;
    private final long downloaded;
    private final long left;
    private final String event;
    private final boolean compact;

    private TrackerRequest(Builder builder) {
        this.infoHash = builder.infoHash;
        this.peerId = builder.peerId;
        this.port = builder.port;
        this.uploaded = builder.uploaded;
        this.downloaded = builder.downloaded;
        this.left = builder.left;
        this.event = builder.event;
        this.compact = builder.compact;
    }

    public byte[] getInfoHash() {
        return infoHash != null ? infoHash.clone() : null;
    }

    public byte[] getPeerId() {
        return peerId != null ? peerId.clone() : null;
    }

    public int getPort() {
        return port;
    }

    public long getUploaded() {
        return uploaded;
    }

    public long getDownloaded() {
        return downloaded;
    }

    public long getLeft() {
        return left;
    }

    public String getEvent() {
        return event;
    }

    public boolean isCompact() {
        return compact;
    }

    public static class Builder {
        private byte[] infoHash;
        private byte[] peerId;
        private int port = 6881;
        private long uploaded = 0;
        private long downloaded = 0;
        private long left = 0;
        private String event;
        private boolean compact = true;

        public Builder infoHash(byte[] infoHash) {
            if (infoHash == null || infoHash.length != 20) {
                throw new IllegalArgumentException("Info hash must be exactly 20 bytes");
            }
            this.infoHash = infoHash.clone();
            return this;
        }

        public Builder peerId(byte[] peerId) {
            if (peerId == null || peerId.length != 20) {
                throw new IllegalArgumentException("Peer ID must be exactly 20 bytes");
            }
            this.peerId = peerId.clone();
            return this;
        }

        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid port range: " + port);
            }
            this.port = port;
            return this;
        }

        public Builder uploaded(long uploaded) {
            if (uploaded < 0) {
                throw new IllegalArgumentException("Uploaded bytes cannot be negative");
            }
            this.uploaded = uploaded;
            return this;
        }

        public Builder downloaded(long downloaded) {
            if (downloaded < 0) {
                throw new IllegalArgumentException("Downloaded bytes cannot be negative");
            }
            this.downloaded = downloaded;
            return this;
        }

        public Builder left(long left) {
            if (left < 0) {
                throw new IllegalArgumentException("Left bytes cannot be negative");
            }
            this.left = left;
            return this;
        }

        public Builder event(String event) {
            this.event = event;
            return this;
        }

        public Builder compact(boolean compact) {
            this.compact = compact;
            return this;
        }

        public TrackerRequest build() {
            if (infoHash == null) {
                throw new IllegalStateException("Info hash must be specified");
            }
            if (peerId == null) {
                throw new IllegalStateException("Peer ID must be specified");
            }
            return new TrackerRequest(this);
        }
    }
}
