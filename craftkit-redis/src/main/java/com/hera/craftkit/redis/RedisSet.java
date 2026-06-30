package com.hera.craftkit.redis;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface RedisSet {

    CompletableFuture<Long> add(String key, String... members);

    CompletableFuture<Long> remove(String key, String... members);

    CompletableFuture<Set<String>> members(String key);

    CompletableFuture<Long> size(String key);

    CompletableFuture<Boolean> contains(String key, String member);

    CompletableFuture<Boolean> expire(String key, Duration ttl);
}
