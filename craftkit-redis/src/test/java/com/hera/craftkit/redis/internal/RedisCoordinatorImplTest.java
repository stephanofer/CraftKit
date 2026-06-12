package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisException;
import com.hera.craftkit.redis.RedisLease;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RedisCoordinatorImplTest {

    @Test
    void tryAcquireLeaseUsesSetNxPxAndServerToken() {
        final FakeExecutor executor = new FakeExecutor();
        final RedisCoordinatorImpl coordinator = new RedisCoordinatorImpl(executor, "server-1", () -> { }, () -> "token");

        final Optional<RedisLease> lease = coordinator.tryAcquireLease("hera:prod:leases:job", Duration.ofSeconds(5)).join();

        assertTrue(lease.isPresent());
        assertEquals("hera:prod:leases:job", executor.acquireKey);
        assertEquals("server-1:token", executor.acquireValue);
        assertEquals(Duration.ofSeconds(5), executor.acquireTtl);
        assertEquals("server-1:token", lease.get().token());
    }

    @Test
    void tryAcquireLeaseReturnsEmptyWhenSetNxFails() {
        final FakeExecutor executor = new FakeExecutor();
        executor.acquireResult = false;
        final RedisCoordinatorImpl coordinator = new RedisCoordinatorImpl(executor, "server-1", () -> { }, () -> "token");

        assertTrue(coordinator.tryAcquireLease("hera:prod:leases:job", Duration.ofSeconds(5)).join().isEmpty());
    }

    @Test
    void releaseUsesLuaAndIsIdempotent() {
        final FakeExecutor executor = new FakeExecutor();
        final RedisCoordinatorImpl coordinator = new RedisCoordinatorImpl(executor, "server-1", () -> { }, () -> "token");
        final RedisLease lease = coordinator.tryAcquireLease("hera:prod:leases:job", Duration.ofSeconds(5)).join().orElseThrow();

        assertTrue(lease.release().join());
        assertFalse(lease.release().join());

        assertEquals(1, executor.releaseCalls.get());
        assertEquals(RedisCoordinatorImpl.RELEASE_SCRIPT, executor.releaseScript);
        assertEquals("hera:prod:leases:job", executor.releaseKeys[0]);
        assertEquals("server-1:token", executor.releaseValues[0]);
    }

    @Test
    void withLeaseReleasesAfterSuccess() {
        final FakeExecutor executor = new FakeExecutor();
        final RedisCoordinatorImpl coordinator = new RedisCoordinatorImpl(executor, "server-1", () -> { }, () -> "token");

        final Optional<String> result = coordinator.withLease(
            "hera:prod:leases:job",
            Duration.ofSeconds(5),
            () -> CompletableFuture.completedFuture("done")
        ).join();

        assertEquals(Optional.of("done"), result);
        assertEquals(1, executor.releaseCalls.get());
    }

    @Test
    void withLeaseReturnsEmptyWhenLeaseNotAcquired() {
        final FakeExecutor executor = new FakeExecutor();
        executor.acquireResult = false;
        final RedisCoordinatorImpl coordinator = new RedisCoordinatorImpl(executor, "server-1", () -> { }, () -> "token");

        final Optional<String> result = coordinator.withLease(
            "hera:prod:leases:job",
            Duration.ofSeconds(5),
            () -> CompletableFuture.completedFuture("done")
        ).join();

        assertTrue(result.isEmpty());
        assertEquals(0, executor.releaseCalls.get());
    }

    @Test
    void withLeaseReleasesWhenSupplierThrowsAndKeepsTaskFailurePrimary() {
        final FakeExecutor executor = new FakeExecutor();
        executor.releaseFailure = new RedisException("release failed");
        final RedisCoordinatorImpl coordinator = new RedisCoordinatorImpl(executor, "server-1", () -> { }, () -> "token");

        final IllegalStateException taskFailure = new IllegalStateException("task failed");
        final CompletionException exception = assertThrows(CompletionException.class, () -> coordinator.withLease(
            "hera:prod:leases:job",
            Duration.ofSeconds(5),
            () -> { throw taskFailure; }
        ).join());

        assertEquals(taskFailure, exception.getCause());
        assertEquals(1, exception.getCause().getSuppressed().length);
        assertEquals(1, executor.releaseCalls.get());
    }

    @Test
    void withLeaseReleasesWhenTaskFutureFailsAndKeepsTaskFailurePrimary() {
        final FakeExecutor executor = new FakeExecutor();
        final RedisCoordinatorImpl coordinator = new RedisCoordinatorImpl(executor, "server-1", () -> { }, () -> "token");
        final IllegalStateException taskFailure = new IllegalStateException("task failed");

        final CompletionException exception = assertThrows(CompletionException.class, () -> coordinator.withLease(
            "hera:prod:leases:job",
            Duration.ofSeconds(5),
            () -> CompletableFuture.failedFuture(taskFailure)
        ).join());

        assertEquals(taskFailure, exception.getCause());
        assertEquals(1, executor.releaseCalls.get());
    }

    @Test
    void withLeaseFailsWhenSuccessfulTaskReleaseFails() {
        final FakeExecutor executor = new FakeExecutor();
        executor.releaseFailure = new RedisException("release failed");
        final RedisCoordinatorImpl coordinator = new RedisCoordinatorImpl(executor, "server-1", () -> { }, () -> "token");

        final CompletionException exception = assertThrows(CompletionException.class, () -> coordinator.withLease(
            "hera:prod:leases:job",
            Duration.ofSeconds(5),
            () -> CompletableFuture.completedFuture("done")
        ).join());

        assertInstanceOf(RedisException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("release"));
    }

    private static final class FakeExecutor implements RedisCommandExecutor {

        private boolean acquireResult = true;
        private RuntimeException releaseFailure;
        private String acquireKey;
        private String acquireValue;
        private Duration acquireTtl;
        private String releaseScript;
        private String[] releaseKeys;
        private String[] releaseValues;
        private final AtomicInteger releaseCalls = new AtomicInteger();

        @Override
        public CompletableFuture<Boolean> setIfAbsent(final String key, final String value, final Duration ttl) {
            this.acquireKey = key;
            this.acquireValue = value;
            this.acquireTtl = ttl;
            return CompletableFuture.completedFuture(this.acquireResult);
        }

        @Override
        public CompletableFuture<Long> evalLong(final String script, final String[] keys, final String... values) {
            this.releaseCalls.incrementAndGet();
            this.releaseScript = script;
            this.releaseKeys = keys;
            this.releaseValues = values;
            if (this.releaseFailure != null) {
                return CompletableFuture.failedFuture(this.releaseFailure);
            }
            return CompletableFuture.completedFuture(1L);
        }
    }
}
