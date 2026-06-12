package com.hera.craftkit.redis;

public class RedisException extends RuntimeException {

    public RedisException(final String message) {
        super(message);
    }

    public RedisException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
