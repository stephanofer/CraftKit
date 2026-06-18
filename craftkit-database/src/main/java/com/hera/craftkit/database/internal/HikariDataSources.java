package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.sql.Driver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class HikariDataSources {

    static final String DEFAULT_MYSQL_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";

    private static final Logger LOGGER = System.getLogger(HikariDataSources.class.getName());
    private static final Map<String, String> DEFAULT_JDBC_PROPERTIES = defaultJdbcProperties();

    private HikariDataSources() {
    }

    static HikariConfig buildConfig(final DatabaseConfig config) {
        final DatabaseConfig resolvedConfig = Objects.requireNonNull(config, "Database config must not be null.");

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(resolvedConfig.pool().poolName());
        hikariConfig.setJdbcUrl(buildJdbcUrl(resolvedConfig));
        hikariConfig.setDriverClassName(resolveDriverClassName(resolvedConfig));
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
            final String resolvedDriverClassName = resolveDriverClassName(resolvedConfig);
            logDriverDiagnostics(resolvedConfig, resolvedDriverClassName);
            return new HikariDataSource(buildConfig(resolvedConfig));
        } catch (final RuntimeException exception) {
            throw new DatabaseException("Failed to create MySQL datasource for " + sanitize(resolvedConfig) + '.', exception);
        }
    }

    static String buildJdbcUrl(final DatabaseConfig config) {
        return "jdbc:mysql://" + config.host() + ':' + config.port() + '/' + config.database();
    }

    static String resolveDriverClassName(final DatabaseConfig config) {
        final String configuredDriverClassName = config.driverClassName();
        return configuredDriverClassName == null ? DEFAULT_MYSQL_DRIVER_CLASS_NAME : configuredDriverClassName;
    }

    static String sanitize(final DatabaseConfig config) {
        return config.host() + ':' + config.port() + '/' + config.database();
    }

    static DriverDiagnostic inspectDriver(final String driverClassName) {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        DriverDiagnostic diagnostic = inspectDriver(driverClassName, contextClassLoader);
        if (diagnostic.loaded()) {
            return diagnostic;
        }

        final ClassLoader fallbackClassLoader = HikariDataSources.class.getClassLoader();
        if (fallbackClassLoader == contextClassLoader) {
            return diagnostic;
        }

        final DriverDiagnostic fallbackDiagnostic = inspectDriver(driverClassName, fallbackClassLoader);
        return fallbackDiagnostic.loaded() ? fallbackDiagnostic : diagnostic;
    }

    private static DriverDiagnostic inspectDriver(final String driverClassName, final ClassLoader classLoader) {
        try {
            final Class<?> driverClass = Class.forName(driverClassName, true, classLoader);
            DriverVersion driverVersion = DriverVersion.unavailable();
            if (Driver.class.isAssignableFrom(driverClass)) {
                driverVersion = instantiateDriver(driverClass.asSubclass(Driver.class));
            }
            return DriverDiagnostic.loaded(
                driverClass.getName(),
                String.valueOf(driverClass.getClassLoader()),
                driverClass.getPackage() == null ? null : driverClass.getPackage().getImplementationVersion(),
                driverVersion.majorVersion(),
                driverVersion.minorVersion()
            );
        } catch (final ClassNotFoundException | LinkageError exception) {
            return DriverDiagnostic.missing(exception);
        }
    }

    private static DriverVersion instantiateDriver(final Class<? extends Driver> driverClass) {
        try {
            final Driver driver = driverClass.getDeclaredConstructor().newInstance();
            return new DriverVersion(driver.getMajorVersion(), driver.getMinorVersion());
        } catch (final InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException exception) {
            return DriverVersion.unavailable();
        }
    }

    private static void logDriverDiagnostics(final DatabaseConfig config, final String resolvedDriverClassName) {
        final DriverDiagnostic diagnostic = inspectDriver(resolvedDriverClassName);
        final DriverDiagnosticLogMessage logMessage = buildDriverDiagnosticLogMessage(config, resolvedDriverClassName, diagnostic);
        LOGGER.log(logMessage.level(), logMessage.message(), logMessage.arguments());
    }

    static DriverDiagnosticLogMessage buildDriverDiagnosticLogMessage(
        final DatabaseConfig config,
        final String resolvedDriverClassName,
        final DriverDiagnostic diagnostic
    ) {
        if (diagnostic.loaded()) {
            return new DriverDiagnosticLogMessage(
                Level.INFO,
                "CraftKit database datasource initialization: pool={0}, target={1}, configuredDriver={2}, loadedDriver={3}, driverClassLoader={4}, implementationVersion={5}, jdbcDriverVersion={6}.",
                new Object[] {
                    config.pool().poolName(),
                    sanitize(config),
                    resolvedDriverClassName,
                    diagnostic.loadedClassName(),
                    diagnostic.classLoader(),
                    diagnostic.implementationVersion() == null ? "unknown" : diagnostic.implementationVersion(),
                    diagnostic.versionText()
                }
            );
        }

        return new DriverDiagnosticLogMessage(
            Level.WARNING,
            "CraftKit database datasource initialization could not inspect JDBC driver: pool={0}, target={1}, configuredDriver={2}, attemptedClassLoaders=thread-context then CraftKit, error={3}. Hikari will still validate the configured driver class.",
            new Object[] {
                config.pool().poolName(),
                sanitize(config),
                resolvedDriverClassName,
                diagnostic.failure() == null ? "unknown" : diagnostic.failure().toString()
            }
        );
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

    record DriverDiagnostic(
        boolean loaded,
        String loadedClassName,
        String classLoader,
        String implementationVersion,
        int majorVersion,
        int minorVersion,
        Throwable failure
    ) {

        static DriverDiagnostic loaded(
            final String loadedClassName,
            final String classLoader,
            final String implementationVersion,
            final int majorVersion,
            final int minorVersion
        ) {
            return new DriverDiagnostic(true, loadedClassName, classLoader, implementationVersion, majorVersion, minorVersion, null);
        }

        static DriverDiagnostic missing(final Throwable failure) {
            return new DriverDiagnostic(false, null, null, null, -1, -1, failure);
        }

        String versionText() {
            if (this.majorVersion < 0 || this.minorVersion < 0) {
                return "unknown";
            }
            return this.majorVersion + "." + this.minorVersion;
        }
    }

    record DriverDiagnosticLogMessage(Level level, String message, Object[] arguments) {
    }

    private record DriverVersion(int majorVersion, int minorVersion) {

        static DriverVersion unavailable() {
            return new DriverVersion(-1, -1);
        }
    }
}
