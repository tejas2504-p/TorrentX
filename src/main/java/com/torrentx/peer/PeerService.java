package com.torrentx.peer;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public interface PeerService {
    void start() throws IOException;
    void stop();
    void connectToPeers(List<Peer> peers);
    Collection<PeerConnection> getActiveConnections();
    void setListener(PeerConnection.PeerConnectionListener listener);
}
