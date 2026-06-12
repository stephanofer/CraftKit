package com.hera.craftkit.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RedisConfig {

    public static final int DEFAULT_PORT = 6379;
    public static final int DEFAULT_DATABASE = 0;
    public static final String DEFAULT_KEY_PREFIX = "hera";
    public static final String DEFAULT_ENVIRONMENT = "default";
    public static final String DEFAULT_SERVER_ID = "unknown";
    public static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    public static final int DEFAULT_REQUEST_QUEUE_SIZE = 10_000;
    public static final int MAX_REQUEST_QUEUE_SIZE = 100_000;
    public static final Duration DEFAULT_RECONNECT_MIN_DELAY = Duration.ofMillis(100);
    public static final Duration DEFAULT_RECONNECT_MAX_DELAY = Duration.ofSeconds(10);
    public static final int DEFAULT_IO_THREADS = 2;
    public static final int DEFAULT_COMPUTATION_THREADS = 2;

    private static final Pattern COMPONENT = Pattern.compile("[a-zA-Z0-9._:-]+");

    private final String host;
    private final int port;
    private final int database;
    private final String username;
    private final String password;
    private final boolean ssl;
    private final boolean verifyPeer;
    private final Duration commandTimeout;
    private final Duration connectTimeout;
    private final Duration shutdownTimeout;
    private final boolean autoReconnect;
    private final boolean pingBeforeActivate;
    private final int requestQueueSize;
    private final Duration reconnectMinDelay;
    private final Duration reconnectMaxDelay;
    private final int ioThreads;
    private final int computationThreads;
    private final String keyPrefix;
    private final String environment;
    private final String serverId;

    private RedisConfig(
        final String host,
        final int port,
        final int database,
        final String username,
        final String password,
        final boolean ssl,
        final boolean verifyPeer,
        final Duration commandTimeout,
        final Duration connectTimeout,
        final Duration shutdownTimeout,
        final boolean autoReconnect,
        final boolean pingBeforeActivate,
        final int requestQueueSize,
        final Duration reconnectMinDelay,
        final Duration reconnectMaxDelay,
        final int ioThreads,
        final int computationThreads,
        final String keyPrefix,
        final String environment,
        final String serverId
    ) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.ssl = ssl;
        this.verifyPeer = verifyPeer;
        this.commandTimeout = commandTimeout;
        this.connectTimeout = connectTimeout;
        this.shutdownTimeout = shutdownTimeout;
        this.autoReconnect = autoReconnect;
        this.pingBeforeActivate = pingBeforeActivate;
        this.requestQueueSize = requestQueueSize;
        this.reconnectMinDelay = reconnectMinDelay;
        this.reconnectMaxDelay = reconnectMaxDelay;
        this.ioThreads = ioThreads;
        this.computationThreads = computationThreads;
        this.keyPrefix = keyPrefix;
        this.environment = environment;
        this.serverId = serverId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String host() { return this.host; }
    public int port() { return this.port; }
    public int database() { return this.database; }
    public String username() { return this.username; }
    public String password() { return this.password; }
    public boolean ssl() { return this.ssl; }
    public boolean verifyPeer() { return this.verifyPeer; }
    public Duration commandTimeout() { return this.commandTimeout; }
    public Duration connectTimeout() { return this.connectTimeout; }
    public Duration shutdownTimeout() { return this.shutdownTimeout; }
    public boolean autoReconnect() { return this.autoReconnect; }
    public boolean pingBeforeActivate() { return this.pingBeforeActivate; }
    public int requestQueueSize() { return this.requestQueueSize; }
    public Duration reconnectMinDelay() { return this.reconnectMinDelay; }
    public Duration reconnectMaxDelay() { return this.reconnectMaxDelay; }
    public int ioThreads() { return this.ioThreads; }
    public int computationThreads() { return this.computationThreads; }
    public String keyPrefix() { return this.keyPrefix; }
    public String environment() { return this.environment; }
    public String serverId() { return this.serverId; }

    @Override
    public String toString() {
        return "RedisConfig[host=" + this.host
            + ", port=" + this.port
            + ", database=" + this.database
            + ", username=" + this.username
            + ", password=<hidden>"
            + ", ssl=" + this.ssl
            + ", verifyPeer=" + this.verifyPeer
            + ", commandTimeout=" + this.commandTimeout
            + ", connectTimeout=" + this.connectTimeout
            + ", shutdownTimeout=" + this.shutdownTimeout
            + ", autoReconnect=" + this.autoReconnect
            + ", pingBeforeActivate=" + this.pingBeforeActivate
            + ", requestQueueSize=" + this.requestQueueSize
            + ", reconnectMinDelay=" + this.reconnectMinDelay
            + ", reconnectMaxDelay=" + this.reconnectMaxDelay
            + ", ioThreads=" + this.ioThreads
            + ", computationThreads=" + this.computationThreads
            + ", keyPrefix=" + this.keyPrefix
            + ", environment=" + this.environment
            + ", serverId=" + this.serverId
            + ']';
    }

    public static final class Builder {

        private String host = "localhost";
        private int port = DEFAULT_PORT;
        private int database = DEFAULT_DATABASE;
        private String username = "";
        private String password = "";
        private boolean ssl;
        private boolean verifyPeer = true;
        private Duration commandTimeout = DEFAULT_COMMAND_TIMEOUT;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
        private boolean autoReconnect = true;
        private boolean pingBeforeActivate = true;
        private int requestQueueSize = DEFAULT_REQUEST_QUEUE_SIZE;
        private Duration reconnectMinDelay = DEFAULT_RECONNECT_MIN_DELAY;
        private Duration reconnectMaxDelay = DEFAULT_RECONNECT_MAX_DELAY;
        private int ioThreads = DEFAULT_IO_THREADS;
        private int computationThreads = DEFAULT_COMPUTATION_THREADS;
        private String keyPrefix = DEFAULT_KEY_PREFIX;
        private String environment = DEFAULT_ENVIRONMENT;
        private String serverId = DEFAULT_SERVER_ID;

        public Builder host(final String host) { this.host = host; return this; }
        public Builder port(final int port) { this.port = port; return this; }
        public Builder database(final int database) { this.database = database; return this; }
        public Builder username(final String username) { this.username = username; return this; }
        public Builder password(final String password) { this.password = password; return this; }
        public Builder ssl(final boolean ssl) { this.ssl = ssl; return this; }
        public Builder verifyPeer(final boolean verifyPeer) { this.verifyPeer = verifyPeer; return this; }
        public Builder commandTimeout(final Duration commandTimeout) { this.commandTimeout = commandTimeout; return this; }
        public Builder connectTimeout(final Duration connectTimeout) { this.connectTimeout = connectTimeout; return this; }
        public Builder shutdownTimeout(final Duration shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; return this; }
        public Builder autoReconnect(final boolean autoReconnect) { this.autoReconnect = autoReconnect; return this; }
        public Builder pingBeforeActivate(final boolean pingBeforeActivate) { this.pingBeforeActivate = pingBeforeActivate; return this; }
        public Builder requestQueueSize(final int requestQueueSize) { this.requestQueueSize = requestQueueSize; return this; }
        public Builder reconnectMinDelay(final Duration reconnectMinDelay) { this.reconnectMinDelay = reconnectMinDelay; return this; }
        public Builder reconnectMaxDelay(final Duration reconnectMaxDelay) { this.reconnectMaxDelay = reconnectMaxDelay; return this; }
        public Builder ioThreads(final int ioThreads) { this.ioThreads = ioThreads; return this; }
        public Builder computationThreads(final int computationThreads) { this.computationThreads = computationThreads; return this; }
        public Builder keyPrefix(final String keyPrefix) { this.keyPrefix = keyPrefix; return this; }
        public Builder environment(final String environment) { this.environment = environment; return this; }
        public Builder serverId(final String serverId) { this.serverId = serverId; return this; }

        public RedisConfig build() {
            final String resolvedHost = requireNonBlank(this.host, "Redis host must not be blank.");
            if (this.port < 1 || this.port > 65_535) {
                throw new RedisException("Redis port must be between 1 and 65535.");
            }
            if (this.database < 0) {
                throw new RedisException("Redis database must be greater than or equal to 0.");
            }
            if (this.requestQueueSize < 1) {
                throw new RedisException("Redis request queue size must be at least 1.");
            }
            if (this.requestQueueSize > MAX_REQUEST_QUEUE_SIZE) {
                throw new RedisException("Redis request queue size must not be greater than " + MAX_REQUEST_QUEUE_SIZE + '.');
            }
            if (this.ioThreads < 1 || this.computationThreads < 1) {
                throw new RedisException("Redis thread counts must be at least 1.");
            }

            final Duration resolvedCommandTimeout = requirePositive(this.commandTimeout, "Redis command timeout must be positive.");
            final Duration resolvedConnectTimeout = requirePositive(this.connectTimeout, "Redis connect timeout must be positive.");
            final Duration resolvedShutdownTimeout = requirePositive(this.shutdownTimeout, "Redis shutdown timeout must be positive.");
            final Duration resolvedReconnectMinDelay = requirePositive(this.reconnectMinDelay, "Redis reconnect minimum delay must be positive.");
            final Duration resolvedReconnectMaxDelay = requirePositive(this.reconnectMaxDelay, "Redis reconnect maximum delay must be positive.");
            if (resolvedReconnectMaxDelay.compareTo(resolvedReconnectMinDelay) < 0) {
                throw new RedisException("Redis reconnect maximum delay must be greater than or equal to the minimum delay.");
            }

            return new RedisConfig(
                resolvedHost,
                this.port,
                this.database,
                requireNonNull(this.username, "Redis username must not be null.").trim(),
                requireNonNull(this.password, "Redis password must not be null."),
                this.ssl,
                this.verifyPeer,
                resolvedCommandTimeout,
                resolvedConnectTimeout,
                resolvedShutdownTimeout,
                this.autoReconnect,
                this.pingBeforeActivate,
                this.requestQueueSize,
                resolvedReconnectMinDelay,
                resolvedReconnectMaxDelay,
                this.ioThreads,
                this.computationThreads,
                validateComponent(this.keyPrefix, "Redis key prefix"),
                validateComponent(this.environment, "Redis environment"),
                validateComponent(this.serverId, "Redis server id")
            );
        }

        private static String validateComponent(final String value, final String name) {
            final String resolved = requireNonBlank(value, name + " must not be blank.");
            if (resolved.length() > 256) {
                throw new RedisException(name + " must not be longer than 256 characters.");
            }
            if (!COMPONENT.matcher(resolved).matches()) {
                throw new RedisException(name + " may only contain letters, numbers, dots, underscores, colons, and hyphens.");
            }
            return resolved;
        }

        private static String requireNonBlank(final String value, final String message) {
            if (value == null) {
                throw new RedisException(message);
            }
            final String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                throw new RedisException(message);
            }
            return trimmed;
        }

        private static String requireNonNull(final String value, final String message) {
            if (value == null) {
                throw new RedisException(message);
            }
            return value;
        }

        private static Duration requirePositive(final Duration duration, final String message) {
            final Duration resolved = Objects.requireNonNull(duration, message);
            if (resolved.isZero() || resolved.isNegative()) {
                throw new RedisException(message);
            }
            return resolved;
        }
    }
}
