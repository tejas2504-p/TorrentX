package com.torrentx.tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model representing a parsed tracker response.
 */
public class TrackerResponse {

    private final String failureReason;
    private final String warningMessage;
    private final int interval;
    private final Integer minInterval;
    private final String trackerId;
    private final int complete;
    private final int incomplete;
    private final List<PeerInfo> peers;

    private TrackerResponse(Builder builder) {
        this.failureReason = builder.failureReason;
        this.warningMessage = builder.warningMessage;
        this.interval = builder.interval;
        this.minInterval = builder.minInterval;
        this.trackerId = builder.trackerId;
        this.complete = builder.complete;
        this.incomplete = builder.incomplete;
        this.peers = builder.peers != null 
                ? Collections.unmodifiableList(new ArrayList<>(builder.peers)) 
                : Collections.emptyList();
    }

    public boolean isSuccessful() {
        return failureReason == null;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public int getInterval() {
        return interval;
    }

    public Integer getMinInterval() {
        return minInterval;
    }

    public String getTrackerId() {
        return trackerId;
    }

    public int getComplete() {
        return complete;
    }

    public int getIncomplete() {
        return incomplete;
    }

    public List<PeerInfo> getPeers() {
        return peers;
    }

    public static class Builder {
        private String failureReason;
        private String warningMessage;
        private int interval = -1;
        private Integer minInterval;
        private String trackerId;
        private int complete = -1;
        private int incomplete = -1;
        private List<PeerInfo> peers;

        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public Builder warningMessage(String warningMessage) {
            this.warningMessage = warningMessage;
            return this;
        }

        public Builder interval(int interval) {
            this.interval = interval;
            return this;
        }

        public Builder minInterval(Integer minInterval) {
            this.minInterval = minInterval;
            return this;
        }

        public Builder trackerId(String trackerId) {
            this.trackerId = trackerId;
            return this;
        }

        public Builder complete(int complete) {
            this.complete = complete;
            return this;
        }

        public Builder incomplete(int incomplete) {
            this.incomplete = incomplete;
            return this;
        }

        public Builder peers(List<PeerInfo> peers) {
            this.peers = peers;
            return this;
        }

        public TrackerResponse build() {
            return new TrackerResponse(this);
        }
    }
}
