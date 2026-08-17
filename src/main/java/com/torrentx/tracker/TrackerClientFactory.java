package com.torrentx.tracker;

public class TrackerClientFactory {
    public static TrackerClient getClient(String announceUrl) {
        if (announceUrl != null && announceUrl.toLowerCase().startsWith("udp://")) {
            return new UdpTrackerClient();
        }
        return new HttpTrackerClient();
    }
}
