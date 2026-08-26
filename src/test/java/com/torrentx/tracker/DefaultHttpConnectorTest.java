package com.torrentx.tracker;

import org.junit.jupiter.api.Test;
import java.net.http.HttpClient;
import static org.junit.jupiter.api.Assertions.*;

class DefaultHttpConnectorTest {

    @Test
    void testConstructors() {
        // Default constructor should succeed
        DefaultHttpConnector connector1 = new DefaultHttpConnector();
        assertNotNull(connector1);

        // Constructor with custom HttpClient should succeed
        HttpClient customClient = HttpClient.newBuilder().build();
        DefaultHttpConnector connector2 = new DefaultHttpConnector(customClient);
        assertNotNull(connector2);

        // Constructor with null HttpClient should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> new DefaultHttpConnector(null));
    }
}
