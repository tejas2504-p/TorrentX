package com.torrentx.tracker;

import com.torrentx.torrent.TorrentMetadata;
import java.util.List;

/**
 * Interface representing a client for tracker communications.
 */
public interface TrackerClient {

    /**
     * Sends an announce request to the tracker to receive a list of active peers.
     *
     * @param metadata the torrent metadata.
     * @param port the client's listening port.
     * @param uploaded the number of bytes uploaded.
     * @param downloaded the number of bytes downloaded.
     * @param left the number of bytes left to download.
     * @return a list of discovered peers.
     * @throws Exception if tracker communication fails.
     */
    List<PeerInfo> announce(TorrentMetadata metadata, int port, long uploaded, long downloaded, long left) throws Exception;
}
