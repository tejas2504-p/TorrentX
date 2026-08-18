package com.torrentx.peer;

import com.torrentx.tracker.PeerInfo;

/**
 * Represents a remote BitTorrent peer and manages its protocol states.
 */
public class Peer {

    private final PeerInfo info;
    private boolean choked = true;
    private boolean interested = false;
    private boolean chokingMe = true;
    private boolean interestedInMe = false;

    /**
     * Constructs a Peer instance based on PeerInfo.
     */
    public Peer(PeerInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("PeerInfo cannot be null");
        }
        this.info = info;
    }

    public PeerInfo getInfo() {
        return info;
    }

    public boolean isChoked() {
        return choked;
    }

    public void setChoked(boolean choked) {
        this.choked = choked;
    }

    public boolean isInterested() {
        return interested;
    }

    public void setInterested(boolean interested) {
        this.interested = interested;
    }

    public boolean isChokingMe() {
        return chokingMe;
    }

    public void setChokingMe(boolean chokingMe) {
        this.chokingMe = chokingMe;
    }

    public boolean isInterestedInMe() {
        return interestedInMe;
    }

    public void setInterestedInMe(boolean interestedInMe) {
        this.interestedInMe = interestedInMe;
    }
}
