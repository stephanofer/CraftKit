package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisCoordinator;
import com.hera.craftkit.redis.RedisException;
import com.hera.craftkit.redis.RedisLease;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class RedisCoordinatorImpl implements RedisCoordinator {

    static final String RELEASE_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisCommandExecutor executor;

    private final String serverId;

    private final Runnable openCheck;

    private final TokenFactory tokenFactory;

    public RedisCoordinatorImpl(final RedisCommandExecutor executor, final String serverId, final Runnable openCheck) {
        this(executor, serverId, openCheck, RedisCoordinatorImpl::newToken);
    }

    RedisCoordinatorImpl(final RedisCommandExecutor executor, final String serverId, final Runnable openCheck, final TokenFactory tokenFactory) {
        this.executor = Objects.requireNonNull(executor, "Redis command executor must not be null.");
        this.serverId = RedisNames.validateValue(serverId, "Redis server id");
        this.openCheck = Objects.requireNonNull(openCheck, "Open check must not be null.");
        this.tokenFactory = Objects.requireNonNull(tokenFactory, "Token factory must not be null.");
    }

    @Override
    public CompletableFuture<Optional<RedisLease>> tryAcquireLease(final String key, final Duration ttl) {
        try {
            this.openCheck.run();
            final String resolvedKey = RedisNames.validateKey(key);
            final Duration resolvedTtl = validateTtl(ttl);
            final String token = this.serverId + ':' + this.tokenFactory.create();
            return this.executor.setIfAbsent(resolvedKey, token, resolvedTtl)
                .thenApply(acquired -> acquired
                    ? Optional.of(new DefaultRedisLease(resolvedKey, token, resolvedTtl, this.executor, this.openCheck))
                    : Optional.empty());
        } catch (final RuntimeException exception) {
            return CompletableFuture.failedFuture(wrap("Failed to acquire Redis lease.", exception));
        }
    }

    @Override
    public <T> CompletableFuture<Optional<T>> withLease(final String key, final Duration ttl, final Supplier<CompletableFuture<T>> task) {
        final Supplier<CompletableFuture<T>> resolvedTask = Objects.requireNonNull(task, "Redis lease task must not be null.");
        return this.tryAcquireLease(key, ttl).thenCompose(lease -> {
            if (lease.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            final RedisLease acquired = lease.get();
            final CompletableFuture<T> taskFuture;
            try {
                taskFuture = Objects.requireNonNull(resolvedTask.get(), "Redis lease task future must not be null.");
            } catch (final Throwable exception) {
                return releaseAfterFailure(acquired, exception);
            }

            return taskFuture.handle((value, taskFailure) -> new TaskOutcome<>(value, unwrap(taskFailure)))
                .thenCompose(outcome -> {
                    if (outcome.failure() != null) {
                        return releaseAfterFailure(acquired, outcome.failure());
                    }
                    return acquired.release().handle((ignored, releaseFailure) -> {
                        if (releaseFailure != null) {
                            throw new RedisException("Failed to release Redis lease after successful task.", unwrap(releaseFailure));
                        }
                        return Optional.ofNullable(outcome.value());
                    });
                });
        });
    }

    private static <T> CompletableFuture<Optional<T>> releaseAfterFailure(final RedisLease lease, final Throwable taskFailure) {
        return lease.release().handle((ignored, releaseFailure) -> {
            final Throwable primary = unwrap(taskFailure);
            if (releaseFailure != null) {
                primary.addSuppressed(unwrap(releaseFailure));
            }
            throw asCompletion(primary);
        });
    }

    private static Duration validateTtl(final Duration ttl) {
        final Duration resolved = Objects.requireNonNull(ttl, "Redis lease TTL must not be null.");
        if (resolved.isZero() || resolved.isNegative()) {
            throw new RedisException("Redis lease TTL must be positive.");
        }
        return resolved;
    }

    private static String newToken() {
        final byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static RedisException wrap(final String message, final RuntimeException exception) {
        if (exception instanceof RedisException redisException) {
            return redisException;
        }
        return new RedisException(message, exception);
    }

    private static Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private static CompletionException asCompletion(final Throwable throwable) {
        if (throwable instanceof CompletionException completionException) {
            return completionException;
        }
        return new CompletionException(throwable);
    }

    @FunctionalInterface
    interface TokenFactory {

        String create();
    }

    private record TaskOutcome<T>(T value, Throwable failure) {
    }
}
