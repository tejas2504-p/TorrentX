package com.torrentx.tracker;

import com.torrentx.bencode.Metainfo;
import com.torrentx.peer.Peer;
import java.util.List;

public interface TrackerClient {
    List<Peer> announce(Metainfo metainfo, byte[] peerId, int port, long uploaded, long downloaded, long left, String event) throws Exception;
}
