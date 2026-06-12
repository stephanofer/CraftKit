package com.hera.craftkit.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface RedisCoordinator {

    CompletableFuture<Optional<RedisLease>> tryAcquireLease(String key, Duration ttl);

    /**
     * Runs the task only when the lease is acquired. The TTL is not auto-renewed and must cover the task duration.
     */
    <T> CompletableFuture<Optional<T>> withLease(String key, Duration ttl, Supplier<CompletableFuture<T>> task);
}
