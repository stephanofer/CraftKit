package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisCache;
import com.hera.craftkit.redis.RedisConfig;
import com.hera.craftkit.redis.RedisException;
import com.hera.craftkit.redis.RedisSet;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.resource.DefaultClientResources;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LettuceRedisClientTest {

    @Test
    void getManyReturnsEmptyMapWithoutMgetForEmptyKeys() {
        final AtomicInteger calls = new AtomicInteger();
        final RedisCache cache = redisCache(keys -> {
            calls.incrementAndGet();
            return completedRedisFuture(List.of());
        });

        final Map<String, String> result = cache.getMany(List.of()).join();

        assertTrue(result.isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void getManyRejectsInvalidKeys() {
        final RedisCache cache = redisCache(keys -> {
            throw new AssertionError("MGET must not run for invalid keys.");
        });

        assertThrows(RedisException.class, () -> cache.getMany(List.of("valid", " ")));
    }

    @Test
    void getManyRejectsNullKeyCollection() {
        final RedisCache cache = redisCache(keys -> {
            throw new AssertionError("MGET must not run for null key collection.");
        });

        final NullPointerException exception = assertThrows(NullPointerException.class, () -> cache.getMany(null));

        assertEquals("Redis keys must not be null.", exception.getMessage());
    }

    @Test
    void getManyDeduplicatesKeysBeforeMget() {
        final AtomicReference<String[]> requestedKeys = new AtomicReference<>();
        final RedisCache cache = redisCache(keys -> {
            requestedKeys.set(keys);
            return completedRedisFuture(List.of());
        });

        cache.getMany(List.of("key:1", "key:2", "key:1")).join();

        assertEquals(List.of("key:1", "key:2"), List.of(requestedKeys.get()));
    }

    @Test
    void getManyIncludesValuesAndOmitsMissingKeys() {
        final RedisCache cache = redisCache(keys ->
            completedRedisFuture(List.of(
                KeyValue.just("key:1", "online"),
                KeyValue.empty("key:2")
            ))
        );

        final Map<String, String> result = cache.getMany(List.of("key:1", "key:2")).join();

        assertEquals(Map.of("key:1", "online"), result);
    }

    @Test
    void getManyResultKeepsFirstOccurrenceOrder() {
        final RedisCache cache = redisCache(keys ->
            completedRedisFuture(List.of(
                KeyValue.just("key:2", "two"),
                KeyValue.just("key:1", "one"),
                KeyValue.just("key:3", "three")
            ))
        );

        final Map<String, String> result = cache.getMany(List.of("key:2", "key:1", "key:2", "key:3")).join();

        assertEquals(List.of("key:2", "key:1", "key:3"), List.copyOf(result.keySet()));
    }

    @Test
    void getManyResultIsNotModifiable() {
        final RedisCache cache = redisCache(keys -> completedRedisFuture(List.of(KeyValue.just("key:1", "online"))));

        final Map<String, String> result = cache.getMany(List.of("key:1")).join();

        assertThrows(UnsupportedOperationException.class, () -> result.put("key:2", "offline"));
    }

    @Test
    void getManyWrapsMgetFailureInRedisException() {
        final RuntimeException lettuceFailure = new RuntimeException("lettuce failure");
        final RedisCache cache = redisCache(keys -> failedRedisFuture(lettuceFailure));

        final CompletionException exception = assertThrows(CompletionException.class, () -> cache.getMany(List.of("key:1")).join());

        assertTrue(exception.getCause() instanceof RedisException);
        assertEquals(lettuceFailure, exception.getCause().getCause());
    }

    @Test
    void setAddPassesValidatedMembersToSadd() {
        final AtomicReference<String> requestedKey = new AtomicReference<>();
        final AtomicReference<String[]> requestedMembers = new AtomicReference<>();
        final RedisSet set = redisSet((proxy, method, args) -> {
            if ("sadd".equals(method.getName())) {
                requestedKey.set((String) args[0]);
                requestedMembers.set((String[]) args[1]);
                return completedRedisFuture(2L);
            }
            throw new AssertionError("Unexpected Redis command: " + method.getName());
        });

        final Long added = set.add("gamekit:server-index:bedwars:arena", "bedwars-arena-01", "bedwars-arena-02").join();

        assertEquals(2L, added);
        assertEquals("gamekit:server-index:bedwars:arena", requestedKey.get());
        assertEquals(List.of("bedwars-arena-01", "bedwars-arena-02"), List.of(requestedMembers.get()));
    }

    @Test
    void setAddRejectsEmptyMembersBeforeSadd() {
        final RedisSet set = redisSet((proxy, method, args) -> {
            throw new AssertionError("SADD must not run for empty members.");
        });

        assertThrows(RedisException.class, () -> set.add("key"));
    }

    @Test
    void setAddRejectsNullMemberBeforeSadd() {
        final RedisSet set = redisSet((proxy, method, args) -> {
            throw new AssertionError("SADD must not run for invalid members.");
        });

        assertThrows(RedisException.class, () -> set.add("key", "valid", null));
    }

    @Test
    void setRemoveUsesSrem() {
        final AtomicReference<String[]> requestedMembers = new AtomicReference<>();
        final RedisSet set = redisSet((proxy, method, args) -> {
            if ("srem".equals(method.getName())) {
                requestedMembers.set((String[]) args[1]);
                return completedRedisFuture(1L);
            }
            throw new AssertionError("Unexpected Redis command: " + method.getName());
        });

        final Long removed = set.remove("party:1:members", "player-1").join();

        assertEquals(1L, removed);
        assertEquals(List.of("player-1"), List.of(requestedMembers.get()));
    }

    @Test
    void setRemoveRejectsEmptyMembersBeforeSrem() {
        final RedisSet set = redisSet((proxy, method, args) -> {
            throw new AssertionError("SREM must not run for empty members.");
        });

        assertThrows(RedisException.class, () -> set.remove("key"));
    }

    @Test
    void setMembersReturnsUnmodifiableSet() {
        final RedisSet set = redisSet((proxy, method, args) -> {
            if ("smembers".equals(method.getName())) {
                return completedRedisFuture(Set.of("bedwars-arena-01", "bedwars-arena-02"));
            }
            throw new AssertionError("Unexpected Redis command: " + method.getName());
        });

        final Set<String> members = set.members("gamekit:server-index:bedwars:arena").join();

        assertEquals(Set.of("bedwars-arena-01", "bedwars-arena-02"), members);
        assertThrows(UnsupportedOperationException.class, () -> members.add("bedwars-arena-03"));
    }

    @Test
    void setMembersRejectsInvalidKeyBeforeSmembers() {
        final RedisSet set = redisSet((proxy, method, args) -> {
            throw new AssertionError("SMEMBERS must not run for invalid keys.");
        });

        assertThrows(RedisException.class, () -> set.members(" "));
    }

    @Test
    void setMembersWrapsSmembersFailureInRedisException() {
        final RuntimeException lettuceFailure = new RuntimeException("lettuce failure");
        final RedisSet set = redisSet((proxy, method, args) -> {
            if ("smembers".equals(method.getName())) {
                return failedRedisFuture(lettuceFailure);
            }
            throw new AssertionError("Unexpected Redis command: " + method.getName());
        });

        final CompletionException exception = assertThrows(CompletionException.class, () -> set.members("key").join());

        assertTrue(exception.getCause() instanceof RedisException);
        assertEquals(lettuceFailure, exception.getCause().getCause());
    }

    @Test
    void setSizeUsesScard() {
        final RedisSet set = redisSet((proxy, method, args) -> {
            if ("scard".equals(method.getName())) {
                return completedRedisFuture(3L);
            }
            throw new AssertionError("Unexpected Redis command: " + method.getName());
        });

        assertEquals(3L, set.size("queue:tickets").join());
    }

    @Test
    void setContainsUsesSismember() {
        final AtomicReference<String> requestedMember = new AtomicReference<>();
        final RedisSet set = redisSet((proxy, method, args) -> {
            if ("sismember".equals(method.getName())) {
                requestedMember.set((String) args[1]);
                return completedRedisFuture(true);
            }
            throw new AssertionError("Unexpected Redis command: " + method.getName());
        });

        assertTrue(set.contains("party:1:members", "player-1").join());
        assertEquals("player-1", requestedMember.get());
    }

    @Test
    void setContainsRejectsNullMemberBeforeSismember() {
        final RedisSet set = redisSet((proxy, method, args) -> {
            throw new AssertionError("SISMEMBER must not run for invalid members.");
        });

        assertThrows(RedisException.class, () -> set.contains("party:1:members", null));
    }

    @Test
    void setExpireUsesPexpireAndRejectsInvalidTtl() {
        final RedisSet set = redisSet((proxy, method, args) -> {
            if ("pexpire".equals(method.getName())) {
                return completedRedisFuture(true);
            }
            throw new AssertionError("Unexpected Redis command: " + method.getName());
        });

        assertTrue(set.expire("gamekit:server-index:bedwars:arena", Duration.ofSeconds(30)).join());
        assertThrows(RedisException.class, () -> set.expire("gamekit:server-index:bedwars:arena", Duration.ZERO));
    }

    @SuppressWarnings("unchecked")
    private static RedisCache redisCache(final Function<String[], RedisFuture<List<KeyValue<String, String>>>> mget) {
        return redisClient((proxy, method, args) -> {
            if ("mget".equals(method.getName())) {
                return mget.apply((String[]) args[0]);
            }
            throw new AssertionError("Unexpected Redis command: " + method.getName());
        });
    }

    private static RedisSet redisSet(final InvocationHandler commandsHandler) {
        return redisClient(commandsHandler);
    }

    @SuppressWarnings("unchecked")
    private static LettuceRedisClient redisClient(final InvocationHandler commandsHandler) {
        final RedisAsyncCommands<String, String> commands = (RedisAsyncCommands<String, String>) Proxy.newProxyInstance(
            LettuceRedisClientTest.class.getClassLoader(),
            new Class<?>[] {RedisAsyncCommands.class},
            commandsHandler
        );
        final StatefulRedisConnection<String, String> connection = (StatefulRedisConnection<String, String>) Proxy.newProxyInstance(
            LettuceRedisClientTest.class.getClassLoader(),
            new Class<?>[] {StatefulRedisConnection.class},
            connectionHandler(commands)
        );
        try {
            final Constructor<LettuceRedisClient> constructor = LettuceRedisClient.class.getDeclaredConstructor(
                RedisConfig.class,
                DefaultClientResources.class,
                io.lettuce.core.RedisClient.class,
                StatefulRedisConnection.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                RedisConfig.builder()
                    .commandTimeout(Duration.ofMillis(100))
                    .shutdownTimeout(Duration.ofMillis(100))
                    .build(),
                null,
                null,
                connection
            );
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError("Failed to create Redis client test fixture.", exception);
        }
    }

    private static InvocationHandler connectionHandler(final RedisAsyncCommands<String, String> commands) {
        return (proxy, method, args) -> {
            if ("async".equals(method.getName())) {
                return commands;
            }
            throw new AssertionError("Unexpected Redis connection method: " + method.getName());
        };
    }

    private static <T> TestRedisFuture<T> completedRedisFuture(final T value) {
        final TestRedisFuture<T> future = new TestRedisFuture<>();
        future.complete(value);
        return future;
    }

    private static <T> TestRedisFuture<T> failedRedisFuture(final Throwable failure) {
        final TestRedisFuture<T> future = new TestRedisFuture<>();
        future.completeExceptionally(failure);
        return future;
    }

    private static final class TestRedisFuture<T> extends CompletableFuture<T> implements RedisFuture<T> {

        @Override
        public String getError() {
            return null;
        }

        @Override
        public boolean await(final long timeout, final TimeUnit unit) {
            return true;
        }
    }
}
