package com.torrentx.download;

public enum PieceState {
    MISSING,
    DOWNLOADING,
    VERIFYING,
    VERIFIED,
    FAILED
}
