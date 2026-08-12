package com.torrentx.peer;

import java.net.InetSocketAddress;
import java.util.Objects;

public class Peer {
    private final String peerId;
    private final InetSocketAddress address;

    public Peer(String peerId, String ip, int port) {
        this.peerId = peerId;
        this.address = new InetSocketAddress(ip, port);
    }

    public Peer(InetSocketAddress address) {
        this.peerId = "";
        this.address = address;
    }

    public String getPeerId() {
        return peerId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Peer peer = (Peer) o;
        return Objects.equals(address, peer.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return address.toString();
    }
}
