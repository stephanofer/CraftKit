package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseException;
import com.hera.craftkit.database.ExecutorConfig;
import com.hera.craftkit.database.MigrationConfig;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HikariDatabaseTest {

    @Test
    void queryRunsOnProvidedExecutor() {
        final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "custom-db-executor");
            thread.setDaemon(true);
            return thread;
        });
        try {
            final HikariDatabase database = new HikariDatabase(
                new StubDataSource(),
                () -> { },
                "ck_",
                executor,
                null,
                ExecutorConfig.builder().threadCount(1).build(),
                new FlywayMigrator(new StubDataSource(), MigrationConfig.builder().enabled(false).build(), "ck_")
            );

            final String threadName = database.query(connection -> Thread.currentThread().getName()).join();

            assertEquals("custom-db-executor", threadName);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closeIsIdempotentAndDoesNotCloseCustomExecutor() {
        final AtomicInteger closeCount = new AtomicInteger();
        final ExecutorService customExecutor = Executors.newSingleThreadExecutor();
        try {
            final HikariDatabase database = new HikariDatabase(
                new StubDataSource(),
                closeCount::incrementAndGet,
                "ck_",
                customExecutor,
                null,
                ExecutorConfig.builder().threadCount(1).build(),
                new FlywayMigrator(new StubDataSource(), MigrationConfig.builder().enabled(false).build(), "ck_")
            );

            database.close();
            database.close();

            assertEquals(1, closeCount.get());
            assertFalse(customExecutor.isShutdown());
        } finally {
            customExecutor.shutdownNow();
        }
    }

    @Test
    void closeShutsDownOwnedExecutor() throws InterruptedException {
        final ExecutorConfig executorConfig = ExecutorConfig.builder().threadCount(1).shutdownTimeoutMillis(100).build();
        final ExecutorService ownedExecutor = DatabaseExecutors.createExecutor(executorConfig);
        final HikariDatabase database = new HikariDatabase(
            new StubDataSource(),
            () -> { },
            "ck_",
            ownedExecutor,
            ownedExecutor,
            executorConfig,
            new FlywayMigrator(new StubDataSource(), MigrationConfig.builder().enabled(false).build(), "ck_")
        );

        database.close();

        assertTrue(ownedExecutor.awaitTermination(1, TimeUnit.SECONDS));
        assertTrue(ownedExecutor.isShutdown());
    }

    @Test
    void queryAfterCloseFailsClearly() {
        final HikariDatabase database = new HikariDatabase(
            new StubDataSource(),
            () -> { },
            "ck_",
            Runnable::run,
            null,
            ExecutorConfig.builder().threadCount(1).build(),
            new FlywayMigrator(new StubDataSource(), MigrationConfig.builder().enabled(false).build(), "ck_")
        );
        database.close();

        final CompletionException exception = assertThrows(CompletionException.class, () -> database.query(connection -> 1).join());

        assertInstanceOf(DatabaseException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("closed"));
    }

    @Test
    void updateAndExecuteAfterCloseFailClearly() {
        final HikariDatabase database = new HikariDatabase(
            new StubDataSource(),
            () -> { },
            "ck_",
            Runnable::run,
            null,
            ExecutorConfig.builder().threadCount(1).build(),
            new FlywayMigrator(new StubDataSource(), MigrationConfig.builder().enabled(false).build(), "ck_")
        );
        database.close();

        final CompletionException updateException = assertThrows(CompletionException.class, () -> database.update(connection -> 1).join());
        final CompletionException executeException = assertThrows(CompletionException.class, () -> database.execute(connection -> { }).join());

        assertInstanceOf(DatabaseException.class, updateException.getCause());
        assertInstanceOf(DatabaseException.class, executeException.getCause());
        assertTrue(updateException.getCause().getMessage().contains("closed"));
        assertTrue(executeException.getCause().getMessage().contains("closed"));
    }

    @Test
    void datasourceAndTableAfterCloseFailClearly() {
        final HikariDatabase database = new HikariDatabase(
            new StubDataSource(),
            () -> { },
            "ck_",
            Runnable::run,
            null,
            ExecutorConfig.builder().threadCount(1).build(),
            new FlywayMigrator(new StubDataSource(), MigrationConfig.builder().enabled(false).build(), "ck_")
        );
        database.close();

        final DatabaseException dataSourceException = assertThrows(DatabaseException.class, database::dataSource);
        final DatabaseException tableException = assertThrows(DatabaseException.class, () -> database.table("players"));

        assertTrue(dataSourceException.getMessage().contains("closed"));
        assertTrue(tableException.getMessage().contains("closed"));
    }

    @Test
    void datasourceEscapeHatchReturnsConfiguredDatasourceWhileOpen() {
        final StubDataSource dataSource = new StubDataSource();
        final HikariDatabase database = new HikariDatabase(
            dataSource,
            () -> { },
            "ck_",
            Runnable::run,
            null,
            ExecutorConfig.builder().threadCount(1).build(),
            new FlywayMigrator(dataSource, MigrationConfig.builder().enabled(false).build(), "ck_")
        );

        assertEquals(dataSource, database.dataSource());
    }

    @Test
    void migrateReturnsCompletedFutureWhenDisabled() {
        final AtomicBoolean closed = new AtomicBoolean();
        final HikariDatabase database = new HikariDatabase(
            new StubDataSource(),
            () -> closed.set(true),
            "ck_",
            Runnable::run,
            null,
            ExecutorConfig.builder().threadCount(1).build(),
            new FlywayMigrator(new StubDataSource(), MigrationConfig.builder().enabled(false).build(), "ck_")
        );

        assertDoesNotThrow(() -> database.migrate().join());
        database.close();
        assertTrue(closed.get());
    }

    @Test
    void enabledMigrateRunsOnConfiguredExecutor() {
        final AtomicReference<String> migrationThread = new AtomicReference<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "migration-db-executor");
            thread.setDaemon(true);
            return thread;
        });
        try {
            final HikariDatabase database = new HikariDatabase(
                new StubDataSource(),
                () -> { },
                "ck_",
                executor,
                null,
                ExecutorConfig.builder().threadCount(1).build(),
                new RecordingMigrator(true, migrationThread)
            );

            database.migrate().join();

            assertEquals("migration-db-executor", migrationThread.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private record RecordingMigrator(boolean isEnabled, AtomicReference<String> migrationThread) implements DatabaseMigrator {

        @Override
        public void migrate() {
            this.migrationThread.set(Thread.currentThread().getName());
        }
    }

    private static final class StubDataSource implements DataSource {

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    if (method.getName().equals("isClosed")) {
                        return false;
                    }
                    if (method.getName().equals("unwrap")) {
                        throw new SQLException("unwrap unsupported");
                    }
                    if (method.getName().equals("isWrapperFor")) {
                        return false;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
            );
        }

        @Override
        public Connection getConnection(final String username, final String password) {
            return this.getConnection();
        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException {
            throw new SQLException("unwrap unsupported");
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(final PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(final int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
