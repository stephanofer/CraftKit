package com.hera.craftkit.database;

import com.hera.craftkit.database.internal.TablePrefixes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DatabaseConfig {

    public static final int DEFAULT_PORT = 3306;

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private final PoolConfig pool;
    private final ExecutorConfig executor;
    private final MigrationConfig migration;
    private final String driverClassName;
    private final Map<String, String> jdbcProperties;

    private DatabaseConfig(
        final String host,
        final int port,
        final String database,
        final String username,
        final String password,
        final String tablePrefix,
        final PoolConfig pool,
        final ExecutorConfig executor,
        final MigrationConfig migration,
        final String driverClassName,
        final Map<String, String> jdbcProperties
    ) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
        this.pool = pool;
        this.executor = executor;
        this.migration = migration;
        this.driverClassName = driverClassName;
        this.jdbcProperties = jdbcProperties;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String host() {
        return this.host;
    }

    public int port() {
        return this.port;
    }

    public String database() {
        return this.database;
    }

    public String username() {
        return this.username;
    }

    public String password() {
        return this.password;
    }

    public String tablePrefix() {
        return this.tablePrefix;
    }

    public PoolConfig pool() {
        return this.pool;
    }

    public ExecutorConfig executor() {
        return this.executor;
    }

    public MigrationConfig migration() {
        return this.migration;
    }

    public String driverClassName() {
        return this.driverClassName;
    }

    public Map<String, String> jdbcProperties() {
        return this.jdbcProperties;
    }

    @Override
    public String toString() {
        return "DatabaseConfig[host=" + this.host
            + ", port=" + this.port
            + ", database=" + this.database
            + ", username=" + this.username
            + ", password=<hidden>"
            + ", tablePrefix=" + this.tablePrefix
            + ", pool=" + this.pool
            + ", executor=" + this.executor
            + ", migration=" + this.migration
            + ", driverClassName=" + this.driverClassName
            + ", jdbcProperties=" + this.jdbcProperties
            + ']';
    }

    public static final class Builder {

        private String host;
        private int port = DEFAULT_PORT;
        private String database;
        private String username;
        private String password = "";
        private String tablePrefix = "";
        private PoolConfig pool = PoolConfig.builder().build();
        private ExecutorConfig.Builder executorBuilder = ExecutorConfig.builder();
        private ExecutorConfig executor;
        private MigrationConfig migration = MigrationConfig.builder().build();
        private String driverClassName;
        private final Map<String, String> jdbcProperties = new LinkedHashMap<>();

        public Builder host(final String host) {
            this.host = host;
            return this;
        }

        public Builder port(final int port) {
            this.port = port;
            return this;
        }

        public Builder database(final String database) {
            this.database = database;
            return this;
        }

        public Builder username(final String username) {
            this.username = username;
            return this;
        }

        public Builder password(final String password) {
            this.password = password;
            return this;
        }

        public Builder tablePrefix(final String tablePrefix) {
            this.tablePrefix = tablePrefix;
            return this;
        }

        public Builder pool(final PoolConfig pool) {
            this.pool = Objects.requireNonNull(pool, "Pool config must not be null.");
            return this;
        }

        public Builder executor(final ExecutorConfig executor) {
            this.executor = Objects.requireNonNull(executor, "Executor config must not be null.");
            this.executorBuilder = null;
            return this;
        }

        public Builder executor(final ExecutorConfig.Builder executorBuilder) {
            this.executorBuilder = Objects.requireNonNull(executorBuilder, "Executor config builder must not be null.");
            this.executor = null;
            return this;
        }

        public Builder migration(final MigrationConfig migration) {
            this.migration = Objects.requireNonNull(migration, "Migration config must not be null.");
            return this;
        }

        public Builder driverClassName(final String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        public Builder jdbcProperties(final Map<String, String> jdbcProperties) {
            this.jdbcProperties.clear();
            this.jdbcProperties.putAll(Objects.requireNonNull(jdbcProperties, "JDBC properties must not be null."));
            return this;
        }

        public Builder putJdbcProperty(final String key, final String value) {
            this.jdbcProperties.put(key, value);
            return this;
        }

        public DatabaseConfig build() {
            final String resolvedHost = requireNonBlank(this.host, "Database host must not be blank.");
            final String resolvedDatabase = requireNonBlank(this.database, "Database name must not be blank.");
            final String resolvedUsername = requireNonBlank(this.username, "Database username must not be blank.");
            final String resolvedPassword = requireNonNullValue(this.password, "Database password must not be null.");

            if (this.port < 1 || this.port > 65_535) {
                throw new DatabaseException("Database port must be between 1 and 65535.");
            }

            final String resolvedTablePrefix = TablePrefixes.validatePrefix(Objects.requireNonNull(this.tablePrefix, "Table prefix must not be null."));
            final PoolConfig resolvedPool = Objects.requireNonNull(this.pool, "Pool config must not be null.");
            final ExecutorConfig resolvedExecutor = this.executor != null
                ? this.executor
                : Objects.requireNonNull(this.executorBuilder, "Executor config must not be null.").build(resolvedPool.maximumPoolSize());
            final MigrationConfig resolvedMigration = Objects.requireNonNull(this.migration, "Migration config must not be null.");
            if (resolvedMigration.enabled()) {
                TablePrefixes.table(resolvedTablePrefix, "flyway_schema_history");
            }
            final String resolvedDriverClassName = optionalNonBlank(this.driverClassName);

            final Map<String, String> validatedJdbcProperties = new LinkedHashMap<>();
            for (final Map.Entry<String, String> entry : this.jdbcProperties.entrySet()) {
                final String key = requireNonBlank(entry.getKey(), "JDBC property key must not be blank.");
                final String value = requireNonNullValue(entry.getValue(), "JDBC property value must not be null.");
                validatedJdbcProperties.put(key, value);
            }

            return new DatabaseConfig(
                resolvedHost,
                this.port,
                resolvedDatabase,
                resolvedUsername,
                resolvedPassword,
                resolvedTablePrefix,
                resolvedPool,
                resolvedExecutor,
                resolvedMigration,
                resolvedDriverClassName,
                Map.copyOf(validatedJdbcProperties)
            );
        }

        private static String optionalNonBlank(final String value) {
            if (value == null) {
                return null;
            }
            final String sanitized = value.trim();
            return sanitized.isEmpty() ? null : sanitized;
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

        private static String requireNonNullValue(final String value, final String message) {
            if (value == null) {
                throw new DatabaseException(message);
            }
            return value;
        }
    }
}
