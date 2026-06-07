package com.hera.craftkit.database;

import java.util.Objects;

public final class PoolConfig {

    public static final String DEFAULT_POOL_NAME = "craftkit-mysql";
    public static final int DEFAULT_MAXIMUM_POOL_SIZE = 10;
    public static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 10_000L;
    public static final long DEFAULT_VALIDATION_TIMEOUT_MILLIS = 5_000L;
    public static final long DEFAULT_IDLE_TIMEOUT_MILLIS = 600_000L;
    public static final long DEFAULT_MAX_LIFETIME_MILLIS = 1_800_000L;
    public static final long DEFAULT_LEAK_DETECTION_THRESHOLD_MILLIS = 0L;

    private final String poolName;
    private final int maximumPoolSize;
    private final Integer minimumIdle;
    private final long connectionTimeoutMillis;
    private final long validationTimeoutMillis;
    private final long idleTimeoutMillis;
    private final long maxLifetimeMillis;
    private final boolean autoCommit;
    private final long leakDetectionThresholdMillis;

    private PoolConfig(
        final String poolName,
        final int maximumPoolSize,
        final Integer minimumIdle,
        final long connectionTimeoutMillis,
        final long validationTimeoutMillis,
        final long idleTimeoutMillis,
        final long maxLifetimeMillis,
        final boolean autoCommit,
        final long leakDetectionThresholdMillis
    ) {
        this.poolName = poolName;
        this.maximumPoolSize = maximumPoolSize;
        this.minimumIdle = minimumIdle;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.validationTimeoutMillis = validationTimeoutMillis;
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.maxLifetimeMillis = maxLifetimeMillis;
        this.autoCommit = autoCommit;
        this.leakDetectionThresholdMillis = leakDetectionThresholdMillis;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String poolName() {
        return this.poolName;
    }

    public int maximumPoolSize() {
        return this.maximumPoolSize;
    }

    public Integer minimumIdle() {
        return this.minimumIdle;
    }

    public long connectionTimeoutMillis() {
        return this.connectionTimeoutMillis;
    }

    public long validationTimeoutMillis() {
        return this.validationTimeoutMillis;
    }

    public long idleTimeoutMillis() {
        return this.idleTimeoutMillis;
    }

    public long maxLifetimeMillis() {
        return this.maxLifetimeMillis;
    }

    public boolean autoCommit() {
        return this.autoCommit;
    }

    public long leakDetectionThresholdMillis() {
        return this.leakDetectionThresholdMillis;
    }

    @Override
    public String toString() {
        return "PoolConfig[poolName=" + this.poolName
            + ", maximumPoolSize=" + this.maximumPoolSize
            + ", minimumIdle=" + this.minimumIdle
            + ", connectionTimeoutMillis=" + this.connectionTimeoutMillis
            + ", validationTimeoutMillis=" + this.validationTimeoutMillis
            + ", idleTimeoutMillis=" + this.idleTimeoutMillis
            + ", maxLifetimeMillis=" + this.maxLifetimeMillis
            + ", autoCommit=" + this.autoCommit
            + ", leakDetectionThresholdMillis=" + this.leakDetectionThresholdMillis
            + ']';
    }

    public static final class Builder {

        private String poolName = DEFAULT_POOL_NAME;
        private int maximumPoolSize = DEFAULT_MAXIMUM_POOL_SIZE;
        private Integer minimumIdle;
        private long connectionTimeoutMillis = DEFAULT_CONNECTION_TIMEOUT_MILLIS;
        private long validationTimeoutMillis = DEFAULT_VALIDATION_TIMEOUT_MILLIS;
        private long idleTimeoutMillis = DEFAULT_IDLE_TIMEOUT_MILLIS;
        private long maxLifetimeMillis = DEFAULT_MAX_LIFETIME_MILLIS;
        private boolean autoCommit = true;
        private long leakDetectionThresholdMillis = DEFAULT_LEAK_DETECTION_THRESHOLD_MILLIS;

        public Builder poolName(final String poolName) {
            this.poolName = poolName;
            return this;
        }

        public Builder maximumPoolSize(final int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public Builder minimumIdle(final Integer minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }

        public Builder connectionTimeoutMillis(final long connectionTimeoutMillis) {
            this.connectionTimeoutMillis = connectionTimeoutMillis;
            return this;
        }

        public Builder validationTimeoutMillis(final long validationTimeoutMillis) {
            this.validationTimeoutMillis = validationTimeoutMillis;
            return this;
        }

        public Builder idleTimeoutMillis(final long idleTimeoutMillis) {
            this.idleTimeoutMillis = idleTimeoutMillis;
            return this;
        }

        public Builder maxLifetimeMillis(final long maxLifetimeMillis) {
            this.maxLifetimeMillis = maxLifetimeMillis;
            return this;
        }

        public Builder autoCommit(final boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        public Builder leakDetectionThresholdMillis(final long leakDetectionThresholdMillis) {
            this.leakDetectionThresholdMillis = leakDetectionThresholdMillis;
            return this;
        }

        public PoolConfig build() {
            final String sanitizedPoolName = requireNonBlank(this.poolName, "Pool name must not be blank.");

            if (this.maximumPoolSize < 1) {
                throw new DatabaseException("Pool maximum size must be at least 1.");
            }
            if (this.minimumIdle != null && (this.minimumIdle < 0 || this.minimumIdle > this.maximumPoolSize)) {
                throw new DatabaseException("Pool minimum idle must be between 0 and maximumPoolSize.");
            }
            if (this.connectionTimeoutMillis <= 0L) {
                throw new DatabaseException("Pool connection timeout must be greater than 0.");
            }
            if (this.validationTimeoutMillis <= 0L) {
                throw new DatabaseException("Pool validation timeout must be greater than 0.");
            }
            if (this.idleTimeoutMillis < 0L) {
                throw new DatabaseException("Pool idle timeout must be non-negative.");
            }
            if (this.maxLifetimeMillis < 0L) {
                throw new DatabaseException("Pool max lifetime must be non-negative.");
            }
            if (this.leakDetectionThresholdMillis < 0L) {
                throw new DatabaseException("Pool leak detection threshold must be non-negative.");
            }

            return new PoolConfig(
                sanitizedPoolName,
                this.maximumPoolSize,
                this.minimumIdle,
                this.connectionTimeoutMillis,
                this.validationTimeoutMillis,
                this.idleTimeoutMillis,
                this.maxLifetimeMillis,
                this.autoCommit,
                this.leakDetectionThresholdMillis
            );
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
