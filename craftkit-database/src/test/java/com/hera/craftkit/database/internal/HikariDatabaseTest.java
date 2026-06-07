package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseException;
import com.hera.craftkit.database.ExecutorConfig;
import com.hera.craftkit.database.MigrationConfig;
import com.hera.craftkit.database.TransactionIsolation;
import com.hera.craftkit.database.TransactionOptions;
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

    @Test
    void transactionCommitsAndRestoresConnectionStateOnSuccess() {
        final TransactionDataSource dataSource = new TransactionDataSource();
        final HikariDatabase database = databaseWith(dataSource, Runnable::run);

        final String result = database.transaction(
            TransactionOptions.builder()
                .isolation(TransactionIsolation.READ_COMMITTED)
                .readOnly(true)
                .build(),
            connection -> {
                assertFalse(connection.getAutoCommit());
                assertTrue(connection.isReadOnly());
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
                return "committed";
            }
        ).join();

        assertEquals("committed", result);
        assertTrue(dataSource.state.committed.get());
        assertFalse(dataSource.state.rolledBack.get());
        assertTrue(dataSource.state.closed.get());
        assertTrue(dataSource.state.autoCommit);
        assertFalse(dataSource.state.readOnly);
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, dataSource.state.isolation);
    }

    @Test
    void transactionRollsBackAndSuppressesRollbackFailureOnError() {
        final TransactionDataSource dataSource = new TransactionDataSource();
        dataSource.state.failRollback = true;
        final HikariDatabase database = databaseWith(dataSource, Runnable::run);

        final CompletionException exception = assertThrows(CompletionException.class, () -> database.transaction(connection -> {
            throw new SQLException("work failed");
        }).join());

        assertInstanceOf(DatabaseException.class, exception.getCause());
        assertTrue(dataSource.state.rolledBack.get());
        assertEquals(1, exception.getCause().getCause().getSuppressed().length);
    }

    @Test
    void transactionFailsWhenCommitFails() {
        final TransactionDataSource dataSource = new TransactionDataSource();
        dataSource.state.failCommit = true;
        final HikariDatabase database = databaseWith(dataSource, Runnable::run);

        final CompletionException exception = assertThrows(CompletionException.class, () -> database.transaction(connection -> "value").join());

        assertInstanceOf(DatabaseException.class, exception.getCause());
        assertTrue(dataSource.state.rolledBack.get());
    }

    @Test
    void transactionAfterCloseFailsClearly() {
        final HikariDatabase database = databaseWith(new TransactionDataSource(), Runnable::run);
        database.close();

        final CompletionException exception = assertThrows(CompletionException.class, () -> database.transaction(connection -> null).join());

        assertInstanceOf(DatabaseException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("closed"));
    }

    @Test
    void transactionRunsOnConfiguredExecutor() {
        final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "transaction-db-executor");
            thread.setDaemon(true);
            return thread;
        });
        try {
            final HikariDatabase database = databaseWith(new TransactionDataSource(), executor);

            final String threadName = database.transaction(connection -> Thread.currentThread().getName()).join();

            assertEquals("transaction-db-executor", threadName);
        } finally {
            executor.shutdownNow();
        }
    }

    private static HikariDatabase databaseWith(final DataSource dataSource, final java.util.concurrent.Executor executor) {
        return new HikariDatabase(
            dataSource,
            () -> { },
            "ck_",
            executor,
            null,
            ExecutorConfig.builder().threadCount(1).build(),
            new FlywayMigrator(dataSource, MigrationConfig.builder().enabled(false).build(), "ck_")
        );
    }

    private record RecordingMigrator(boolean isEnabled, AtomicReference<String> migrationThread) implements DatabaseMigrator {

        @Override
        public void migrate() {
            this.migrationThread.set(Thread.currentThread().getName());
        }
    }

    private static final class TransactionDataSource implements DataSource {

        private final TransactionConnectionState state = new TransactionConnectionState();

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> this.state.autoCommit;
                    case "setAutoCommit" -> {
                        this.state.autoCommit = (boolean) args[0];
                        yield null;
                    }
                    case "isReadOnly" -> this.state.readOnly;
                    case "setReadOnly" -> {
                        this.state.readOnly = (boolean) args[0];
                        yield null;
                    }
                    case "getTransactionIsolation" -> this.state.isolation;
                    case "setTransactionIsolation" -> {
                        this.state.isolation = (int) args[0];
                        yield null;
                    }
                    case "commit" -> {
                        this.state.committed.set(true);
                        if (this.state.failCommit) {
                            throw new SQLException("commit failed");
                        }
                        yield null;
                    }
                    case "rollback" -> {
                        this.state.rolledBack.set(true);
                        if (this.state.failRollback) {
                            throw new SQLException("rollback failed");
                        }
                        yield null;
                    }
                    case "close" -> {
                        this.state.closed.set(true);
                        yield null;
                    }
                    case "isClosed" -> this.state.closed.get();
                    case "unwrap" -> throw new SQLException("unwrap unsupported");
                    case "isWrapperFor" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
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

    private static final class TransactionConnectionState {

        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_REPEATABLE_READ;
        private boolean failCommit;
        private boolean failRollback;
        private final AtomicBoolean committed = new AtomicBoolean();
        private final AtomicBoolean rolledBack = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
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
