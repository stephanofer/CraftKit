package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisCache;
import com.hera.craftkit.redis.RedisClient;
import com.hera.craftkit.redis.RedisConfig;
import com.hera.craftkit.redis.RedisCoordinator;
import com.hera.craftkit.redis.RedisException;
import com.hera.craftkit.redis.RedisMessage;
import com.hera.craftkit.redis.RedisMessageHandler;
import com.hera.craftkit.redis.RedisPublisher;
import com.hera.craftkit.redis.RedisState;
import com.hera.craftkit.redis.RedisSubscriber;
import com.hera.craftkit.redis.RedisSubscription;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.Delay;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class LettuceRedisClient implements RedisClient, RedisCache, RedisState, RedisPublisher, RedisSubscriber, RedisCommandExecutor {

    private final RedisConfig config;
    private final DefaultClientResources resources;
    private final io.lettuce.core.RedisClient client;
    private final StatefulRedisConnection<String, String> commandConnection;
    private final RedisAsyncCommands<String, String> commands;
    private final RedisCoordinator coordinator;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object pubSubLock = new Object();
    private final RedisPubSubListener<String, String> listener = new Listener();
    private final PubSubSubscriptions subscriptions = new PubSubSubscriptions();
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;

    private LettuceRedisClient(
        final RedisConfig config,
        final DefaultClientResources resources,
        final io.lettuce.core.RedisClient client,
        final StatefulRedisConnection<String, String> commandConnection
    ) {
        this.config = config;
        this.resources = resources;
        this.client = client;
        this.commandConnection = commandConnection;
        this.commands = commandConnection.async();
        this.coordinator = new RedisCoordinatorImpl(this, config.serverId(), this::ensureOpen);
    }

    public static LettuceRedisClient create(final RedisConfig config) {
        final DefaultClientResources resources = DefaultClientResources.builder()
            .ioThreadPoolSize(config.ioThreads())
            .computationThreadPoolSize(config.computationThreads())
            .reconnectDelay(Delay.exponential(config.reconnectMinDelay(), config.reconnectMaxDelay(), 2, TimeUnit.MILLISECONDS))
            .build();
        io.lettuce.core.RedisClient client = null;
        StatefulRedisConnection<String, String> connection = null;
        try {
            final RedisURI uri = uri(config);
            client = io.lettuce.core.RedisClient.create(resources, uri);
            client.setOptions(options(config));
            connection = client.connect(StringCodec.UTF8, uri);
            connection.setTimeout(config.commandTimeout());
            return new LettuceRedisClient(config, resources, client, connection);
        } catch (final RuntimeException exception) {
            closeQuietly(connection, exception);
            closeQuietly(client, exception);
            shutdownQuietly(resources, config.shutdownTimeout(), exception);
            throw exception instanceof RedisException redisException
                ? redisException
                : new RedisException("Failed to connect to Redis.", exception);
        }
    }

    @Override
    public CompletableFuture<Boolean> ping() {
        return command("ping", commands -> commands.ping()).thenApply("PONG"::equalsIgnoreCase);
    }

    @Override
    public RedisCache cache() {
        return this;
    }

    @Override
    public RedisState state() {
        return this;
    }

    @Override
    public RedisPublisher publisher() {
        return this;
    }

    @Override
    public RedisSubscriber subscriber() {
        return this;
    }

    @Override
    public RedisCoordinator coordinator() {
        return this.coordinator;
    }

    @Override
    public String key(final String domain, final String... parts) {
        this.ensureOpen();
        return RedisNames.buildKey(this.config.keyPrefix(), this.config.environment(), domain, parts);
    }

    @Override
    public String channel(final String domain, final String event) {
        this.ensureOpen();
        return RedisNames.buildChannel(this.config.keyPrefix(), this.config.environment(), domain, event);
    }

    @Override
    public CompletableFuture<String> get(final String key) {
        final String resolvedKey = RedisNames.validateKey(key);
        return command("get", commands -> commands.get(resolvedKey));
    }

    @Override
    public CompletableFuture<Map<String, String>> getMany(final Collection<String> keys) {
        return getMany(keys, resolvedKeys -> command("mget", commands -> commands.mget(resolvedKeys)));
    }

    @Override
    public CompletableFuture<Boolean> set(final String key, final String value, final Duration ttl) {
        final String resolvedKey = RedisNames.validateKey(key);
        final String resolvedValue = RedisNames.validateValue(value, "Redis value");
        final Duration resolvedTtl = validateTtl(ttl);
        return command("set", commands -> commands.set(resolvedKey, resolvedValue, new SetArgs().px(resolvedTtl)))
            .thenApply("OK"::equalsIgnoreCase);
    }

    @Override
    public CompletableFuture<Boolean> setIfAbsent(final String key, final String value, final Duration ttl) {
        final String resolvedKey = RedisNames.validateKey(key);
        final String resolvedValue = RedisNames.validateValue(value, "Redis value");
        final Duration resolvedTtl = validateTtl(ttl);
        return command("set-if-absent", commands -> commands.set(resolvedKey, resolvedValue, new SetArgs().nx().px(resolvedTtl)))
            .thenApply(result -> "OK".equalsIgnoreCase(result));
    }

    @Override
    public CompletableFuture<Boolean> expire(final String key, final Duration ttl) {
        final String resolvedKey = RedisNames.validateKey(key);
        final Duration resolvedTtl = validateTtl(ttl);
        return command("expire", commands -> commands.pexpire(resolvedKey, resolvedTtl));
    }

    @Override
    public CompletableFuture<Boolean> delete(final String key) {
        final String resolvedKey = RedisNames.validateKey(key);
        return command("delete", commands -> commands.del(resolvedKey)).thenApply(deleted -> deleted > 0L);
    }

    @Override
    public CompletableFuture<Long> unlink(final String... keys) {
        if (keys == null || keys.length == 0) {
            throw new RedisException("Redis unlink requires at least one key.");
        }
        final String[] resolvedKeys = new String[keys.length];
        for (int index = 0; index < keys.length; index++) {
            resolvedKeys[index] = RedisNames.validateKey(keys[index]);
        }
        return command("unlink", commands -> commands.unlink(resolvedKeys));
    }

    @Override
    public CompletableFuture<Duration> ttl(final String key) {
        final String resolvedKey = RedisNames.validateKey(key);
        return command("ttl", commands -> commands.pttl(resolvedKey))
            .thenApply(milliseconds -> milliseconds > 0L ? Duration.ofMillis(milliseconds) : Duration.ZERO);
    }

    @Override
    public CompletableFuture<Long> increment(final String key) {
        final String resolvedKey = RedisNames.validateKey(key);
        return command("increment", commands -> commands.incr(resolvedKey));
    }

    @Override
    public CompletableFuture<Long> incrementBy(final String key, final long amount) {
        final String resolvedKey = RedisNames.validateKey(key);
        return command("increment-by", commands -> commands.incrby(resolvedKey, amount));
    }

    @Override
    public CompletableFuture<Boolean> putIfAbsent(final String key, final String value, final Duration ttl) {
        return this.setIfAbsent(key, value, ttl);
    }

    @Override
    public CompletableFuture<String> getAndDelete(final String key) {
        final String resolvedKey = RedisNames.validateKey(key);
        return command("get-and-delete", commands -> commands.getdel(resolvedKey));
    }

    @Override
    public CompletableFuture<Long> publish(final String channel, final String payload) {
        final String resolvedChannel = RedisNames.validateChannel(channel);
        final String resolvedPayload = RedisNames.validateValue(payload, "Redis publish payload");
        return command("publish", commands -> commands.publish(resolvedChannel, resolvedPayload));
    }

    @Override
    public RedisSubscription subscribe(final String channel, final RedisMessageHandler handler) {
        final String resolvedChannel = RedisNames.validateChannel(channel);
        return this.subscribe(resolvedChannel, false, handler);
    }

    @Override
    public RedisSubscription subscribePattern(final String pattern, final RedisMessageHandler handler) {
        final String resolvedPattern = RedisNames.validatePattern(pattern);
        return this.subscribe(resolvedPattern, true, handler);
    }

    @Override
    public CompletableFuture<Long> evalLong(final String script, final String[] keys, final String... values) {
        final String resolvedScript = RedisNames.validateValue(script, "Redis Lua script");
        final String[] resolvedKeys = Objects.requireNonNull(keys, "Redis Lua keys must not be null.").clone();
        for (int index = 0; index < resolvedKeys.length; index++) {
            resolvedKeys[index] = RedisNames.validateKey(resolvedKeys[index]);
        }
        final String[] resolvedValues = values == null ? new String[0] : values.clone();
        for (int index = 0; index < resolvedValues.length; index++) {
            resolvedValues[index] = RedisNames.validateValue(resolvedValues[index], "Redis Lua value");
        }
        return command("eval", commands -> commands.eval(resolvedScript, ScriptOutputType.INTEGER, resolvedKeys, resolvedValues));
    }

    @Override
    public boolean isClosed() {
        return this.closed.get();
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        RedisException failure = null;
        this.subscriptions.closeAll();
        synchronized (this.pubSubLock) {
            failure = closeAppend(failure, "Failed to close Redis pub/sub connection.", this.pubSubConnection);
            this.pubSubConnection = null;
        }
        failure = closeAppend(failure, "Failed to close Redis command connection.", this.commandConnection);
        failure = closeAppend(failure, "Failed to close Redis client.", this.client);
        try {
            this.resources.shutdown(0, this.config.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS).await(this.config.shutdownTimeout().toMillis());
        } catch (final RuntimeException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failure = append(failure, new RedisException("Failed to shut down Redis client resources.", exception));
        }
        if (failure != null) {
            throw failure;
        }
    }

    private RedisSubscription subscribe(final String topic, final boolean pattern, final RedisMessageHandler handler) {
        this.ensureOpen();
        final RedisMessageHandler resolvedHandler = Objects.requireNonNull(handler, "Redis message handler must not be null.");
        final RedisSubscription subscription = pattern
            ? this.subscriptions.addPattern(topic, resolvedHandler, () -> this.unsubscribe(topic, true))
            : this.subscriptions.addChannel(topic, resolvedHandler, () -> this.unsubscribe(topic, false));
        final StatefulRedisPubSubConnection<String, String> connection = this.pubSubConnection();
        final CompletableFuture<Void> subscribeFuture = pattern
            ? adapt("subscribe-pattern", connection.async().psubscribe(topic))
            : adapt("subscribe", connection.async().subscribe(topic));
        subscribeFuture.exceptionally(exception -> {
            subscription.close();
            return null;
        });
        return subscription;
    }

    private void unsubscribe(final String topic, final boolean pattern) {
        final StatefulRedisPubSubConnection<String, String> connection = this.pubSubConnection;
        if (connection == null || this.closed.get()) {
            return;
        }
        if (pattern) {
            connection.async().punsubscribe(topic);
        } else {
            connection.async().unsubscribe(topic);
        }
    }

    private StatefulRedisPubSubConnection<String, String> pubSubConnection() {
        synchronized (this.pubSubLock) {
            this.ensureOpen();
            if (this.pubSubConnection != null) {
                return this.pubSubConnection;
            }
            try {
                final StatefulRedisPubSubConnection<String, String> connection = this.client.connectPubSub(StringCodec.UTF8, uri(this.config));
                connection.setTimeout(this.config.commandTimeout());
                connection.addListener(this.listener);
                this.pubSubConnection = connection;
                return connection;
            } catch (final RuntimeException exception) {
                throw new RedisException("Failed to connect Redis pub/sub connection.", exception);
            }
        }
    }

    private <T> CompletableFuture<T> command(final String operation, final Function<RedisAsyncCommands<String, String>, RedisFuture<T>> command) {
        try {
            this.ensureOpen();
            return adapt(operation, command.apply(this.commands));
        } catch (final RuntimeException exception) {
            return CompletableFuture.failedFuture(exception instanceof RedisException
                ? exception
                : new RedisException("Failed to execute Redis " + operation + '.', exception));
        }
    }

    static CompletableFuture<Map<String, String>> getMany(
        final Collection<String> keys,
        final Function<String[], CompletableFuture<List<KeyValue<String, String>>>> mget
    ) {
        Objects.requireNonNull(keys, "Redis keys must not be null.");
        final LinkedHashSet<String> resolvedKeys = new LinkedHashSet<>(keys.size());
        for (final String key : keys) {
            resolvedKeys.add(RedisNames.validateKey(key));
        }
        if (resolvedKeys.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return mget.apply(resolvedKeys.toArray(String[]::new))
            .thenApply(LettuceRedisClient::toMgetResultMap);
    }

    static Map<String, String> toMgetResultMap(final List<KeyValue<String, String>> values) {
        final LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (final KeyValue<String, String> value : values) {
            if (value.hasValue()) {
                result.put(value.getKey(), value.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new RedisException("Cannot execute Redis operation because the client is closed.");
        }
    }

    private static <T> CompletableFuture<T> adapt(final String operation, final RedisFuture<T> redisFuture) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        redisFuture.whenComplete((value, failure) -> {
            if (failure != null) {
                future.completeExceptionally(failure instanceof RedisException
                    ? failure
                    : new RedisException("Failed to execute Redis " + operation + '.', failure));
                return;
            }
            future.complete(value);
        });
        return future;
    }

    private static Duration validateTtl(final Duration ttl) {
        final Duration resolved = Objects.requireNonNull(ttl, "Redis TTL must not be null.");
        if (resolved.isZero() || resolved.isNegative()) {
            throw new RedisException("Redis TTL must be positive.");
        }
        return resolved;
    }

    private static RedisURI uri(final RedisConfig config) {
        final RedisURI.Builder builder = RedisURI.builder()
            .withHost(config.host())
            .withPort(config.port())
            .withDatabase(config.database())
            .withSsl(config.ssl())
            .withVerifyPeer(config.verifyPeer())
            .withTimeout(config.commandTimeout());
        if (!config.username().isEmpty()) {
            builder.withAuthentication(config.username(), config.password());
        } else if (!config.password().isEmpty()) {
            builder.withPassword(config.password());
        }
        return builder.build();
    }

    private static ClientOptions options(final RedisConfig config) {
        return ClientOptions.builder()
            .autoReconnect(config.autoReconnect())
            .pingBeforeActivateConnection(config.pingBeforeActivate())
            .requestQueueSize(config.requestQueueSize())
            .socketOptions(SocketOptions.builder().connectTimeout(config.connectTimeout()).build())
            .build();
    }

    private static RedisException closeAppend(final RedisException failure, final String message, final AutoCloseable closeable) {
        if (closeable == null) {
            return failure;
        }
        try {
            closeable.close();
            return failure;
        } catch (final Exception exception) {
            return append(failure, new RedisException(message, exception));
        }
    }

    private static RedisException append(final RedisException failure, final RedisException next) {
        if (failure == null) {
            return next;
        }
        failure.addSuppressed(next);
        return failure;
    }

    private static void closeQuietly(final AutoCloseable closeable, final RuntimeException failure) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (final Exception exception) {
            failure.addSuppressed(exception);
        }
    }

    private static void shutdownQuietly(final DefaultClientResources resources, final Duration timeout, final RuntimeException failure) {
        try {
            resources.shutdown(0, timeout.toMillis(), TimeUnit.MILLISECONDS).await(timeout.toMillis());
        } catch (final RuntimeException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failure.addSuppressed(exception);
        }
    }

    private final class Listener implements RedisPubSubListener<String, String> {

        @Override
        public void message(final String channel, final String message) {
            subscriptions.dispatchChannel(channel, message);
        }

        @Override
        public void message(final String pattern, final String channel, final String message) {
            subscriptions.dispatchPattern(pattern, channel, message);
        }

        @Override public void subscribed(final String channel, final long count) { }
        @Override public void psubscribed(final String pattern, final long count) { }
        @Override public void unsubscribed(final String channel, final long count) { }
        @Override public void punsubscribed(final String pattern, final long count) { }

    }
}
