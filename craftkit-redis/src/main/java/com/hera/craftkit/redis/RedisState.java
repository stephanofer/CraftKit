package com.hera.craftkit.redis;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface RedisState {

    CompletableFuture<Long> increment(String key);

    CompletableFuture<Long> incrementBy(String key, long amount);

    CompletableFuture<Boolean> putIfAbsent(String key, String value, Duration ttl);

    CompletableFuture<String> getAndDelete(String key);
}
