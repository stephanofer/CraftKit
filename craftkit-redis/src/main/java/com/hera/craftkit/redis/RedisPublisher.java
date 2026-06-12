package com.hera.craftkit.redis;

import java.util.concurrent.CompletableFuture;

public interface RedisPublisher {

    CompletableFuture<Long> publish(String channel, String payload);
}
