package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisCache;
import com.hera.craftkit.redis.RedisConfig;
import com.hera.craftkit.redis.RedisException;
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

    @SuppressWarnings("unchecked")
    private static RedisCache redisCache(final Function<String[], RedisFuture<List<KeyValue<String, String>>>> mget) {
        final RedisAsyncCommands<String, String> commands = (RedisAsyncCommands<String, String>) Proxy.newProxyInstance(
            LettuceRedisClientTest.class.getClassLoader(),
            new Class<?>[] {RedisAsyncCommands.class},
            (proxy, method, args) -> {
                if ("mget".equals(method.getName())) {
                    return mget.apply((String[]) args[0]);
                }
                throw new AssertionError("Unexpected Redis command: " + method.getName());
            }
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
            throw new AssertionError("Failed to create Redis cache test fixture.", exception);
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
