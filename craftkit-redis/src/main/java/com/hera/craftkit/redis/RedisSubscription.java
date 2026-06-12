package com.hera.craftkit.redis;

public interface RedisSubscription extends AutoCloseable {

    boolean isClosed();

    @Override
    void close();
}
