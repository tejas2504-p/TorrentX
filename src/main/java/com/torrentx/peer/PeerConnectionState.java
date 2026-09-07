package com.torrentx.peer;

public enum PeerConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    HANDSHAKING,
    READY,
    CLOSING,
    FAILED
}
