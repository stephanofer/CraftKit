package com.hera.craftkit.redis;

import java.util.concurrent.CompletableFuture;

public interface RedisClient extends AutoCloseable {

    CompletableFuture<Boolean> ping();

    RedisCache cache();

    RedisState state();

    RedisPublisher publisher();

    RedisSubscriber subscriber();

    RedisCoordinator coordinator();

    String key(String domain, String... parts);

    String channel(String domain, String event);

    boolean isClosed();

    @Override
    void close();
}
