package com.torrentx.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoggerTest {

    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = new Logger(LoggerTest.class);
    }

    @Test
    void testLoggingDoesNotThrowExceptions() {
        // Verify all info variants
        assertDoesNotThrow(() -> logger.info("Test Info Message"));
        assertDoesNotThrow(() -> logger.info("Test Info Message with arg: {}", "hello"));

        // Verify all debug variants
        assertDoesNotThrow(() -> logger.debug("Test Debug Message"));
        assertDoesNotThrow(() -> logger.debug("Test Debug Message with args: {} / {}", 123, true));

        // Verify all warn variants
        assertDoesNotThrow(() -> logger.warn("Test Warn Message"));
        assertDoesNotThrow(() -> logger.warn("Test Warn Message with arg: {}", 45.67));

        // Verify all error variants
        assertDoesNotThrow(() -> logger.error("Test Error Message"));
        assertDoesNotThrow(() -> logger.error("Test Error Message with args: {}", "failed"));
        assertDoesNotThrow(() -> logger.error("Test Error Message with cause", new RuntimeException("Underlying cause")));
    }
}
