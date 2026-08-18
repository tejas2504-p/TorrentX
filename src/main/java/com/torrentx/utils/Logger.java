package com.torrentx.utils;

import org.slf4j.LoggerFactory;

/**
 * A wrapper class around SLF4J Logger, providing simplified logging functions.
 */
public class Logger {

    private final org.slf4j.Logger slf4jLogger;

    /**
     * Constructs a Logger wrapper for the specified class.
     *
     * @param clazz the class to construct the logger for.
     */
    public Logger(Class<?> clazz) {
        this.slf4jLogger = LoggerFactory.getLogger(clazz);
    }

    /**
     * Log an info message.
     *
     * @param message the message string.
     */
    public void info(String message) {
        slf4jLogger.info(message);
    }

    /**
     * Log an info message with arguments.
     *
     * @param format the format string.
     * @param arguments the arguments list.
     */
    public void info(String format, Object... arguments) {
        slf4jLogger.info(format, arguments);
    }

    /**
     * Log a debug message.
     *
     * @param message the message string.
     */
    public void debug(String message) {
        slf4jLogger.debug(message);
    }

    /**
     * Log a warning message.
     *
     * @param message the message string.
     */
    public void warn(String message) {
        slf4jLogger.warn(message);
    }

    /**
     * Log an error message.
     *
     * @param message the message string.
     * @param throwable the exception cause.
     */
    public void error(String message, Throwable throwable) {
        slf4jLogger.error(message, throwable);
    }
}
