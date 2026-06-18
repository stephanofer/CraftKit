package com.hera.craftkit.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface RedisCache {

    CompletableFuture<String> get(String key);

    CompletableFuture<Map<String, String>> getMany(Collection<String> keys);

    CompletableFuture<Boolean> set(String key, String value, Duration ttl);

    CompletableFuture<Boolean> setIfAbsent(String key, String value, Duration ttl);

    CompletableFuture<Boolean> expire(String key, Duration ttl);

    CompletableFuture<Boolean> delete(String key);

    CompletableFuture<Long> unlink(String... keys);

    /**
     * Returns {@link Duration#ZERO} when Redis reports the key as missing or without an expiry.
     */
    CompletableFuture<Duration> ttl(String key);
}
