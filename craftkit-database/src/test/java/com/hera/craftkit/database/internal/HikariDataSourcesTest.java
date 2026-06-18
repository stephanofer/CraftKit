package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.PoolConfig;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HikariDataSourcesTest {

    @Test
    void buildConfigUsesExpectedDefaultsAndOverrides() {
        final DatabaseConfig config = DatabaseConfig.builder()
            .host("db.example.net")
            .port(3307)
            .database("craftkit")
            .username("hera")
            .password("secret")
            .pool(PoolConfig.builder().maximumPoolSize(12).minimumIdle(2).build())
            .putJdbcProperty("cachePrepStmts", "false")
            .putJdbcProperty("socketTimeout", "4000")
            .build();

        final HikariConfig hikariConfig = HikariDataSources.buildConfig(config);

        assertEquals("jdbc:mysql://db.example.net:3307/craftkit", hikariConfig.getJdbcUrl());
        assertEquals("hera", hikariConfig.getUsername());
        assertEquals("secret", hikariConfig.getPassword());
        assertEquals("com.mysql.cj.jdbc.Driver", hikariConfig.getDriverClassName());
        assertEquals(12, hikariConfig.getMaximumPoolSize());
        assertEquals(2, hikariConfig.getMinimumIdle());
        assertEquals("false", hikariConfig.getDataSourceProperties().getProperty("cachePrepStmts"));
        assertEquals("4000", hikariConfig.getDataSourceProperties().getProperty("socketTimeout"));
        assertEquals("true", hikariConfig.getDataSourceProperties().getProperty("rewriteBatchedStatements"));
        assertEquals(-1L, hikariConfig.getInitializationFailTimeout());
    }

    @Test
    void buildConfigRespectsConsumerDriverOverride() {
        final DatabaseConfig config = baseConfig()
            .driverClassName("com.mysql.cj.jdbc.NonRegisteringDriver")
            .build();

        final HikariConfig hikariConfig = HikariDataSources.buildConfig(config);

        assertEquals("com.mysql.cj.jdbc.NonRegisteringDriver", hikariConfig.getDriverClassName());
    }

    @Test
    void blankDriverOverrideFallsBackToMysqlDefault() {
        final DatabaseConfig config = baseConfig()
            .driverClassName(" ")
            .build();

        assertEquals("com.mysql.cj.jdbc.Driver", HikariDataSources.resolveDriverClassName(config));
    }

    @Test
    void sanitizedTargetDoesNotExposePassword() {
        final DatabaseConfig config = baseConfig()
            .password("super-secret")
            .putJdbcProperty("password", "also-secret")
            .build();

        final String sanitizedTarget = HikariDataSources.sanitize(config);

        assertEquals("db.example.net:3306/craftkit", sanitizedTarget);
        assertFalse(sanitizedTarget.contains("super-secret"));
        assertFalse(sanitizedTarget.contains("also-secret"));
    }

    @Test
    void driverDiagnosticLogMessageDoesNotExposeSecrets() {
        final DatabaseConfig config = baseConfig()
            .password("super-secret")
            .putJdbcProperty("password", "also-secret")
            .pool(PoolConfig.builder().poolName("craftkit-test-pool").build())
            .build();
        final HikariDataSources.DriverDiagnostic diagnostic = HikariDataSources.DriverDiagnostic.loaded(
            MetadataFreeDriver.class.getName(),
            "test-classloader",
            null,
            8,
            1
        );

        final HikariDataSources.DriverDiagnosticLogMessage logMessage = HikariDataSources.buildDriverDiagnosticLogMessage(
            config,
            MetadataFreeDriver.class.getName(),
            diagnostic
        );
        final String logPayload = renderLogPayload(logMessage);

        assertTrue(logPayload.contains("craftkit-test-pool"));
        assertTrue(logPayload.contains("db.example.net:3306/craftkit"));
        assertTrue(logPayload.contains(MetadataFreeDriver.class.getName()));
        assertTrue(logPayload.contains("test-classloader"));
        assertTrue(logPayload.contains("unknown"));
        assertTrue(logPayload.contains("8.1"));
        assertFalse(logPayload.contains("super-secret"));
        assertFalse(logPayload.contains("also-secret"));
        assertFalse(logPayload.contains("password=also-secret"));
        assertFalse(logPayload.contains("jdbc:mysql://db.example.net:3306/craftkit?password=also-secret"));
    }

    @Test
    void driverInspectionToleratesMissingPackageImplementationVersion() {
        final HikariDataSources.DriverDiagnostic diagnostic = HikariDataSources.inspectDriver(MetadataFreeDriver.class.getName());

        assertTrue(diagnostic.loaded());
        assertEquals(MetadataFreeDriver.class.getName(), diagnostic.loadedClassName());
        assertEquals("1.2", diagnostic.versionText());
    }

    @Test
    void driverInspectionFallsBackToCraftKitClassLoaderWhenContextClassLoaderCannotLoadDriver() {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new ClassLoader(null) {
        });

        try {
            final HikariDataSources.DriverDiagnostic diagnostic = HikariDataSources.inspectDriver(MetadataFreeDriver.class.getName());

            assertTrue(diagnostic.loaded());
            assertEquals(MetadataFreeDriver.class.getName(), diagnostic.loadedClassName());
            assertEquals(String.valueOf(MetadataFreeDriver.class.getClassLoader()), diagnostic.classLoader());
            assertEquals("1.2", diagnostic.versionText());
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static DatabaseConfig.Builder baseConfig() {
        return DatabaseConfig.builder()
            .host("db.example.net")
            .database("craftkit")
            .username("hera")
            .password("secret");
    }

    private static String renderLogPayload(final HikariDataSources.DriverDiagnosticLogMessage logMessage) {
        return logMessage.message() + ' ' + Arrays.toString(logMessage.arguments());
    }

    public static final class MetadataFreeDriver implements Driver {

        @Override
        public Connection connect(final String url, final Properties info) throws SQLException {
            return null;
        }

        @Override
        public boolean acceptsURL(final String url) throws SQLException {
            return false;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 2;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
