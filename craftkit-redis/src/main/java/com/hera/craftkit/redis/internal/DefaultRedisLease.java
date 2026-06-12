package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisException;
import com.hera.craftkit.redis.RedisLease;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class DefaultRedisLease implements RedisLease {

    private final String key;
    private final String token;
    private final Duration ttl;
    private final RedisCommandExecutor executor;
    private final Runnable openCheck;
    private final AtomicBoolean released = new AtomicBoolean();

    DefaultRedisLease(
        final String key,
        final String token,
        final Duration ttl,
        final RedisCommandExecutor executor,
        final Runnable openCheck
    ) {
        this.key = RedisNames.validateKey(key);
        this.token = RedisNames.validateValue(token, "Redis lease token");
        this.ttl = Objects.requireNonNull(ttl, "Redis lease TTL must not be null.");
        this.executor = Objects.requireNonNull(executor, "Redis command executor must not be null.");
        this.openCheck = Objects.requireNonNull(openCheck, "Open check must not be null.");
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String token() {
        return this.token;
    }

    @Override
    public Duration ttl() {
        return this.ttl;
    }

    @Override
    public CompletableFuture<Boolean> release() {
        if (!this.released.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            this.openCheck.run();
            return this.executor.evalLong(RedisCoordinatorImpl.RELEASE_SCRIPT, new String[] {this.key}, this.token)
                .thenApply(deleted -> deleted == 1L);
        } catch (final RuntimeException exception) {
            return CompletableFuture.failedFuture(exception instanceof RedisException
                ? exception
                : new RedisException("Failed to release Redis lease.", exception));
        }
    }
}
