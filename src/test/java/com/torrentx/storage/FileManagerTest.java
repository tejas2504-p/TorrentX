package com.torrentx.storage;

import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class FileManagerTest {

    @Test
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new FileManager(null));
    }

    @Test
    void testGetDownloadDirectory() {
        File dir = new File("downloads/test");
        FileManager manager = new FileManager(dir);
        assertEquals(dir, manager.getDownloadDirectory());
    }

    @Test
    void testUnsupportedOperationsThrowException() {
        FileManager manager = new FileManager(new File("downloads/test"));
        assertThrows(UnsupportedOperationException.class, () -> manager.allocateFiles());
        assertThrows(UnsupportedOperationException.class, () -> manager.close());
    }
}
