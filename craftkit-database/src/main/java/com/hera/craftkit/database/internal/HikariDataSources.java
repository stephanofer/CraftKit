package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class HikariDataSources {

    private static final Map<String, String> DEFAULT_JDBC_PROPERTIES = defaultJdbcProperties();

    private HikariDataSources() {
    }

    static HikariConfig buildConfig(final DatabaseConfig config) {
        final DatabaseConfig resolvedConfig = Objects.requireNonNull(config, "Database config must not be null.");

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(resolvedConfig.pool().poolName());
        hikariConfig.setJdbcUrl(buildJdbcUrl(resolvedConfig));
        hikariConfig.setUsername(resolvedConfig.username());
        hikariConfig.setPassword(resolvedConfig.password());
        hikariConfig.setMaximumPoolSize(resolvedConfig.pool().maximumPoolSize());
        if (resolvedConfig.pool().minimumIdle() != null) {
            hikariConfig.setMinimumIdle(resolvedConfig.pool().minimumIdle());
        }
        hikariConfig.setConnectionTimeout(resolvedConfig.pool().connectionTimeoutMillis());
        hikariConfig.setValidationTimeout(resolvedConfig.pool().validationTimeoutMillis());
        hikariConfig.setIdleTimeout(resolvedConfig.pool().idleTimeoutMillis());
        hikariConfig.setMaxLifetime(resolvedConfig.pool().maxLifetimeMillis());
        hikariConfig.setAutoCommit(resolvedConfig.pool().autoCommit());
        hikariConfig.setLeakDetectionThreshold(resolvedConfig.pool().leakDetectionThresholdMillis());
        hikariConfig.setInitializationFailTimeout(-1L);

        final Map<String, String> jdbcProperties = new LinkedHashMap<>(DEFAULT_JDBC_PROPERTIES);
        jdbcProperties.putAll(resolvedConfig.jdbcProperties());
        jdbcProperties.forEach(hikariConfig::addDataSourceProperty);
        return hikariConfig;
    }

    public static HikariDataSource create(final DatabaseConfig config) {
        final DatabaseConfig resolvedConfig = Objects.requireNonNull(config, "Database config must not be null.");
        try {
            return new HikariDataSource(buildConfig(resolvedConfig));
        } catch (final RuntimeException exception) {
            throw new DatabaseException("Failed to create MySQL datasource for " + sanitize(resolvedConfig) + '.', exception);
        }
    }

    static String buildJdbcUrl(final DatabaseConfig config) {
        return "jdbc:mysql://" + config.host() + ':' + config.port() + '/' + config.database();
    }

    private static Map<String, String> defaultJdbcProperties() {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("cachePrepStmts", "true");
        properties.put("prepStmtCacheSize", "250");
        properties.put("prepStmtCacheSqlLimit", "2048");
        properties.put("useServerPrepStmts", "true");
        properties.put("rewriteBatchedStatements", "true");
        return Map.copyOf(properties);
    }

    private static String sanitize(final DatabaseConfig config) {
        return config.host() + ':' + config.port() + '/' + config.database();
    }
}
