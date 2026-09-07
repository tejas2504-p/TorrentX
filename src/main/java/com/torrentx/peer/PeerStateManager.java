package com.torrentx.peer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class PeerStateManager {
    private static final Logger LOGGER = Logger.getLogger(PeerStateManager.class.getName());
    
    private final AtomicReference<PeerConnectionState> currentState;

    public PeerStateManager() {
        this.currentState = new AtomicReference<>(PeerConnectionState.CONNECTING);
    }

    public PeerConnectionState getState() {
        return currentState.get();
    }

    /**
     * Attempts to transition to the target state.
     * @param targetState the desired state
     * @return true if successful or already in state
     * @throws IllegalStateException if the transition is invalid
     */
    public boolean transition(PeerConnectionState targetState) {
        while (true) {
            PeerConnectionState current = currentState.get();
            if (current == targetState) {
                return true;
            }
            if (!isValidTransition(current, targetState)) {
                LOGGER.warning("Invalid state transition from " + current + " to " + targetState);
                throw new IllegalStateException("Invalid state transition from " + current + " to " + targetState);
            }
            if (currentState.compareAndSet(current, targetState)) {
                LOGGER.fine("State transitioned from " + current + " to " + targetState);
                return true;
            }
        }
    }

    private boolean isValidTransition(PeerConnectionState from, PeerConnectionState to) {
        switch (from) {
            case DISCONNECTED:
                return to == PeerConnectionState.CONNECTING;
            case CONNECTING:
                return to == PeerConnectionState.CONNECTED || to == PeerConnectionState.FAILED || to == PeerConnectionState.CLOSING;
            case CONNECTED:
                return to == PeerConnectionState.HANDSHAKING || to == PeerConnectionState.FAILED || to == PeerConnectionState.CLOSING;
            case HANDSHAKING:
                return to == PeerConnectionState.READY || to == PeerConnectionState.FAILED || to == PeerConnectionState.CLOSING;
            case READY:
                return to == PeerConnectionState.CLOSING || to == PeerConnectionState.FAILED;
            case CLOSING:
                return to == PeerConnectionState.DISCONNECTED || to == PeerConnectionState.FAILED;
            case FAILED:
                return to == PeerConnectionState.DISCONNECTED || to == PeerConnectionState.CLOSING;
            default:
                return false;
        }
    }
}
