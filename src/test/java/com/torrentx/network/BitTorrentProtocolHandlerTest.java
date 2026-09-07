package com.torrentx.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class BitTorrentProtocolHandlerTest {

    private BitTorrentProtocolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BitTorrentProtocolHandler();
    }

    @Test
    void testReadWriteKeepAlive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.writeMessage(Message.keepAlive(), out);
        
        byte[] bytes = out.toByteArray();
        assertEquals(4, bytes.length);
        assertArrayEquals(new byte[]{0, 0, 0, 0}, bytes);
        
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        Message message = handler.readMessage(in);
        
        assertNotNull(message);
        assertTrue(message.isKeepAlive());
    }

    @Test
    void testReadWriteStandardMessage() throws Exception {
        byte[] payload = {0x01, 0x02, 0x03};
        Message msg = new Message((byte) 5, payload);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        handler.writeMessage(msg, out);
        
        byte[] bytes = out.toByteArray();
        // length prefix = 4 bytes (value = 4)
        // id = 1 byte
        // payload = 3 bytes
        assertEquals(8, bytes.length);
        assertEquals(0, bytes[0]);
        assertEquals(0, bytes[1]);
        assertEquals(0, bytes[2]);
        assertEquals(4, bytes[3]); // 1 (id) + 3 (payload) = 4
        assertEquals(5, bytes[4]); // ID
        assertEquals(0x01, bytes[5]);
        
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        Message parsed = handler.readMessage(in);
        
        assertFalse(parsed.isKeepAlive());
        assertEquals(5, parsed.getId());
        assertArrayEquals(payload, parsed.getPayload());
    }

    @Test
    void testReadInvalidLengthNegative() {
        // Negative length
        byte[] bytes = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        
        ProtocolException ex = assertThrows(ProtocolException.class, () -> handler.readMessage(in));
        assertTrue(ex.getMessage().contains("Invalid message length"));
    }

    @Test
    void testReadInvalidLengthTooLarge() {
        // Exceeds MAX_MESSAGE_SIZE (32768)
        byte[] bytes = {0, 0, (byte) 0xFF, (byte) 0xFF}; // 65535
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        
        ProtocolException ex = assertThrows(ProtocolException.class, () -> handler.readMessage(in));
        assertTrue(ex.getMessage().contains("Invalid message length"));
    }

    @Test
    void testReadTruncatedMessage() {
        // Length 5, but stream only has 2 bytes total after length prefix
        byte[] bytes = {0, 0, 0, 5, 1, 2};
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        
        assertThrows(java.io.EOFException.class, () -> handler.readMessage(in));
    }

    @Test
    void testPartialTCPReads() throws Exception {
        // Length 4 (ID + 3 payload)
        byte[] bytes = {0, 0, 0, 4, 9, 10, 11, 12};
        
        // Custom InputStream that forces 1-byte reads
        InputStream fragmentedIn = new InputStream() {
            private int pos = 0;
            @Override
            public int read() {
                if (pos >= bytes.length) return -1;
                return bytes[pos++] & 0xFF;
            }
            @Override
            public int read(byte[] b, int off, int len) {
                if (pos >= bytes.length) return -1;
                b[off] = bytes[pos++];
                return 1;
            }
        };

        Message msg = handler.readMessage(fragmentedIn);
        
        assertNotNull(msg);
        assertFalse(msg.isKeepAlive());
        assertEquals(9, msg.getId());
        assertArrayEquals(new byte[]{10, 11, 12}, msg.getPayload());
    }
}
