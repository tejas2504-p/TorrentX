package com.torrentx.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HaveMessageTest {

    @Test
    void testValidHaveMessage() throws ProtocolException {
        Message msg = Message.have(5); // Piece index 5
        HaveMessage have = new HaveMessage(msg, 10); // pieceCount 10
        
        assertEquals(5, have.getPieceIndex());
    }

    @Test
    void testInvalidPieceIndexNegative() {
        Message msg = Message.have(-1);
        assertThrows(ProtocolException.class, () -> new HaveMessage(msg, 10));
    }

    @Test
    void testInvalidPieceIndexTooLarge() {
        Message msg = Message.have(10);
        assertThrows(ProtocolException.class, () -> new HaveMessage(msg, 10)); // max is 9
    }
    
    @Test
    void testInvalidMessageType() {
        Message msg = Message.choke();
        assertThrows(IllegalArgumentException.class, () -> new HaveMessage(msg, 10));
    }

    @Test
    void testInvalidPayloadLength() {
        assertThrows(IllegalArgumentException.class, () -> new Message(Message.ID_HAVE, new byte[]{0, 0, 0}));
    }
}
