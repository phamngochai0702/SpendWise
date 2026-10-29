package com.spendwise.repository;

/**
 * Wraps low-level JDBC/SQLite failures into a consistent unchecked exception
 * with a user-presentable Vietnamese message, so Swing code never has to deal
 * with {@link java.sql.SQLException} directly.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String userMessage) {
        super(userMessage);
    }

    public DataAccessException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
