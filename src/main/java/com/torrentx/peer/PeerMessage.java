package com.torrentx.peer;

import java.nio.ByteBuffer;

public interface PeerMessage {
    int getMessageId();
    ByteBuffer toByteBuffer();
}
