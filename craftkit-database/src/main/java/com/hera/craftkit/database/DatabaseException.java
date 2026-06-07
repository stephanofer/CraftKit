package com.hera.craftkit.database;

public final class DatabaseException extends RuntimeException {

    public DatabaseException(final String message) {
        super(message);
    }

    public DatabaseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
