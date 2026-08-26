package com.torrentx.tracker;

/**
 * Maintains the session state for a torrent download/upload session.
 * Tracks bytes uploaded, downloaded, and remaining (left), and governs the lifecycle state
 * transitions (started, completed, stopped events) according to the BitTorrent specification.
 */
public class TrackerSession {
    private final byte[] infoHash;
    private final byte[] peerId;
    private final int port;
    private final long totalLength;

    private long uploaded;
    private long downloaded;
    private long left;

    private boolean startedSent;
    private boolean completedSent;
    private boolean stoppedSent;

    /**
     * Constructs a new TrackerSession.
     *
     * @param infoHash the 20-byte info hash of the torrent.
     * @param peerId the 20-byte client peer ID.
     * @param port the port number the client is listening on.
     * @param totalLength the total length of the torrent in bytes.
     * @param downloaded the number of bytes already downloaded.
     */
    public TrackerSession(byte[] infoHash, byte[] peerId, int port, long totalLength, long downloaded) {
        if (infoHash == null || infoHash.length != 20) {
            throw new IllegalArgumentException("Info hash must be exactly 20 bytes");
        }
        if (peerId == null || peerId.length != 20) {
            throw new IllegalArgumentException("Peer ID must be exactly 20 bytes");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port range: " + port);
        }
        if (totalLength < 0) {
            throw new IllegalArgumentException("Total length cannot be negative");
        }
        if (downloaded < 0 || downloaded > totalLength) {
            throw new IllegalArgumentException("Downloaded bytes must be non-negative and not exceed total length");
        }
        this.infoHash = infoHash.clone();
        this.peerId = peerId.clone();
        this.port = port;
        this.totalLength = totalLength;
        this.downloaded = downloaded;
        this.uploaded = 0;
        this.left = totalLength - downloaded;
        this.startedSent = false;
        // If the torrent is already complete at the start, completed event is not sent later
        this.completedSent = (this.left == 0);
        this.stoppedSent = false;
    }

    /**
     * Updates the session statistics.
     *
     * @param uploaded the new absolute count of uploaded bytes.
     * @param downloaded the new absolute count of downloaded bytes.
     */
    public synchronized void updateStats(long uploaded, long downloaded) {
        if (uploaded < this.uploaded) {
            throw new IllegalArgumentException("Uploaded bytes cannot decrease: " + uploaded + " < " + this.uploaded);
        }
        if (downloaded < this.downloaded) {
            throw new IllegalArgumentException("Downloaded bytes cannot decrease: " + downloaded + " < " + this.downloaded);
        }
        if (downloaded > totalLength) {
            throw new IllegalArgumentException("Downloaded bytes cannot exceed total length: " + downloaded + " > " + totalLength);
        }
        this.uploaded = uploaded;
        this.downloaded = downloaded;
        this.left = totalLength - downloaded;
    }

    /**
     * Generates a started announce request.
     * Sets the session state to started.
     *
     * @return the TrackerRequest.
     */
    public synchronized TrackerRequest createStartedRequest() {
        if (startedSent) {
            throw new IllegalStateException("Started event has already been sent");
        }
        startedSent = true;
        stoppedSent = false; // Reset stopped state if restarted
        return new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .port(port)
                .uploaded(uploaded)
                .downloaded(downloaded)
                .left(left)
                .event("started")
                .build();
    }

    /**
     * Generates a periodic update announce request, or a completed announce request
     * if the torrent just transitioned to complete.
     *
     * @return the TrackerRequest.
     */
    public synchronized TrackerRequest createUpdateRequest() {
        if (!startedSent) {
            throw new IllegalStateException("Cannot send update request before starting the session");
        }
        String event = null;
        if (left == 0 && !completedSent) {
            event = "completed";
            completedSent = true;
        }
        return new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .port(port)
                .uploaded(uploaded)
                .downloaded(downloaded)
                .left(left)
                .event(event)
                .build();
    }

    /**
     * Generates a stopped announce request.
     * Prevents duplicate stopped requests by returning null if already stopped or not started.
     *
     * @return the TrackerRequest, or null if stopped request is unnecessary.
     */
    public synchronized TrackerRequest createStoppedRequest() {
        if (!startedSent || stoppedSent) {
            return null;
        }
        stoppedSent = true;
        startedSent = false; // Reset started state
        return new TrackerRequest.Builder()
                .infoHash(infoHash)
                .peerId(peerId)
                .port(port)
                .uploaded(uploaded)
                .downloaded(downloaded)
                .left(left)
                .event("stopped")
                .build();
    }

    public synchronized byte[] getInfoHash() {
        return infoHash.clone();
    }

    public synchronized byte[] getPeerId() {
        return peerId.clone();
    }

    public synchronized int getPort() {
        return port;
    }

    public synchronized long getTotalLength() {
        return totalLength;
    }

    public synchronized long getUploaded() {
        return uploaded;
    }

    public synchronized long getDownloaded() {
        return downloaded;
    }

    public synchronized long getLeft() {
        return left;
    }

    public synchronized boolean isStartedSent() {
        return startedSent;
    }

    public synchronized boolean isCompletedSent() {
        return completedSent;
    }

    public synchronized boolean isStoppedSent() {
        return stoppedSent;
    }
}
