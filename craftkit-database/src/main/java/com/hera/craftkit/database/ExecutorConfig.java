package com.hera.craftkit.database;

import java.util.Objects;

public final class ExecutorConfig {

    public static final String DEFAULT_THREAD_NAME_PREFIX = "craftkit-database";
    public static final boolean DEFAULT_DAEMON = true;
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 10_000L;

    private final int threadCount;
    private final String threadNamePrefix;
    private final boolean daemon;
    private final long shutdownTimeoutMillis;

    private ExecutorConfig(
        final int threadCount,
        final String threadNamePrefix,
        final boolean daemon,
        final long shutdownTimeoutMillis
    ) {
        this.threadCount = threadCount;
        this.threadNamePrefix = threadNamePrefix;
        this.daemon = daemon;
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int threadCount() {
        return this.threadCount;
    }

    public String threadNamePrefix() {
        return this.threadNamePrefix;
    }

    public boolean daemon() {
        return this.daemon;
    }

    public long shutdownTimeoutMillis() {
        return this.shutdownTimeoutMillis;
    }

    @Override
    public String toString() {
        return "ExecutorConfig[threadCount=" + this.threadCount
            + ", threadNamePrefix=" + this.threadNamePrefix
            + ", daemon=" + this.daemon
            + ", shutdownTimeoutMillis=" + this.shutdownTimeoutMillis
            + ']';
    }

    public static final class Builder {

        private Integer threadCount;
        private String threadNamePrefix = DEFAULT_THREAD_NAME_PREFIX;
        private boolean daemon = DEFAULT_DAEMON;
        private long shutdownTimeoutMillis = DEFAULT_SHUTDOWN_TIMEOUT_MILLIS;

        public Builder threadCount(final int threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public Builder threadNamePrefix(final String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
            return this;
        }

        public Builder daemon(final boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        public Builder shutdownTimeoutMillis(final long shutdownTimeoutMillis) {
            this.shutdownTimeoutMillis = shutdownTimeoutMillis;
            return this;
        }

        public ExecutorConfig build() {
            return this.build(PoolConfig.DEFAULT_MAXIMUM_POOL_SIZE);
        }

        public ExecutorConfig build(final int defaultThreadCount) {
            final int resolvedThreadCount = this.threadCount == null ? defaultThreadCount : this.threadCount;
            final String resolvedPrefix = requireNonBlank(this.threadNamePrefix, "Executor thread name prefix must not be blank.");

            if (resolvedThreadCount < 1) {
                throw new DatabaseException("Executor thread count must be at least 1.");
            }
            if (this.shutdownTimeoutMillis <= 0L) {
                throw new DatabaseException("Executor shutdown timeout must be greater than 0.");
            }

            return new ExecutorConfig(resolvedThreadCount, resolvedPrefix, this.daemon, this.shutdownTimeoutMillis);
        }

        private static String requireNonBlank(final String value, final String message) {
            if (value == null) {
                throw new DatabaseException(message);
            }
            final String sanitized = value.trim();
            if (sanitized.isEmpty()) {
                throw new DatabaseException(message);
            }
            return sanitized;
        }
    }
}
