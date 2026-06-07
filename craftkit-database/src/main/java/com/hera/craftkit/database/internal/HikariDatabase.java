package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.DatabaseException;
import com.hera.craftkit.database.ExecutorConfig;
import com.hera.craftkit.database.SqlOperation;
import com.hera.craftkit.database.SqlQuery;
import com.hera.craftkit.database.SqlTransaction;
import com.hera.craftkit.database.SqlUpdate;
import com.hera.craftkit.database.TransactionOptions;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HikariDatabase implements Database {

    private final DataSource dataSource;
    private final AutoCloseable closeableDataSource;
    private final String tablePrefix;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final ExecutorConfig ownedExecutorConfig;
    private final DatabaseMigrator migrator;
    private final AtomicBoolean closed = new AtomicBoolean();

    public HikariDatabase(
        final DataSource dataSource,
        final AutoCloseable closeableDataSource,
        final String tablePrefix,
        final Executor executor,
        final ExecutorService ownedExecutor,
        final ExecutorConfig ownedExecutorConfig,
        final DatabaseMigrator migrator
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null.");
        this.closeableDataSource = Objects.requireNonNull(closeableDataSource, "Closeable data source must not be null.");
        this.tablePrefix = TablePrefixes.validatePrefix(Objects.requireNonNull(tablePrefix, "Table prefix must not be null."));
        this.executor = Objects.requireNonNull(executor, "Executor must not be null.");
        this.ownedExecutor = ownedExecutor;
        this.ownedExecutorConfig = ownedExecutorConfig;
        this.migrator = Objects.requireNonNull(migrator, "Database migrator must not be null.");
    }

    @Override
    public CompletableFuture<Void> migrate() {
        if (!this.migrator.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return this.submit("migrate", () -> {
            this.migrator.migrate();
            return null;
        });
    }

    @Override
    public <T> CompletableFuture<T> query(final SqlQuery<T> query) {
        final SqlQuery<T> resolvedQuery = Objects.requireNonNull(query, "Query must not be null.");
        return this.submit("query", () -> {
            try (var connection = this.dataSource.getConnection()) {
                return resolvedQuery.execute(connection);
            } catch (final SQLException exception) {
                throw new DatabaseException("Failed to execute database query.", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> update(final SqlUpdate update) {
        final SqlUpdate resolvedUpdate = Objects.requireNonNull(update, "Update must not be null.");
        return this.submit("update", () -> {
            try (var connection = this.dataSource.getConnection()) {
                return resolvedUpdate.execute(connection);
            } catch (final SQLException exception) {
                throw new DatabaseException("Failed to execute database update.", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> execute(final SqlOperation operation) {
        final SqlOperation resolvedOperation = Objects.requireNonNull(operation, "Operation must not be null.");
        return this.submit("execute", () -> {
            try (var connection = this.dataSource.getConnection()) {
                resolvedOperation.execute(connection);
                return null;
            } catch (final SQLException exception) {
                throw new DatabaseException("Failed to execute database operation.", exception);
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> transaction(final SqlTransaction<T> transaction) {
        return this.transaction(TransactionOptions.defaults(), transaction);
    }

    @Override
    public <T> CompletableFuture<T> transaction(final TransactionOptions options, final SqlTransaction<T> transaction) {
        final TransactionOptions resolvedOptions = Objects.requireNonNull(options, "Transaction options must not be null.");
        final SqlTransaction<T> resolvedTransaction = Objects.requireNonNull(transaction, "Transaction must not be null.");
        return this.submit("transaction", () -> this.executeTransaction(resolvedOptions, resolvedTransaction));
    }

    @Override
    public DataSource dataSource() {
        this.ensureOpen("access the database datasource");
        return this.dataSource;
    }

    @Override
    public String tablePrefix() {
        return this.tablePrefix;
    }

    @Override
    public String table(final String name) {
        this.ensureOpen("resolve a database table name");
        return TablePrefixes.table(this.tablePrefix, name);
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

        DatabaseException failure = null;
        try {
            this.closeableDataSource.close();
        } catch (final Exception exception) {
            failure = new DatabaseException("Failed to close the database datasource.", exception);
        }

        if (this.ownedExecutor != null) {
            try {
                DatabaseExecutors.shutdown(this.ownedExecutor, Objects.requireNonNull(this.ownedExecutorConfig, "Owned executor config must not be null."));
            } catch (final RuntimeException exception) {
                if (failure == null) {
                    failure = new DatabaseException("Failed to shut down the owned database executor.", exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private <T> CompletableFuture<T> submit(final String operation, final CheckedSupplier<T> supplier) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(closedException(operation));
        }

        final CompletableFuture<T> future = new CompletableFuture<>();
        try {
            this.executor.execute(() -> {
                if (this.closed.get()) {
                    future.completeExceptionally(closedException(operation));
                    return;
                }

                try {
                    future.complete(supplier.get());
                } catch (final Throwable exception) {
                    future.completeExceptionally(wrapFailure(operation, exception));
                }
            });
        } catch (final RejectedExecutionException exception) {
            future.completeExceptionally(new DatabaseException("Failed to schedule database " + operation + " because the executor is not accepting new tasks.", exception));
        }
        return future;
    }

    private <T> T executeTransaction(final TransactionOptions options, final SqlTransaction<T> transaction) {
        try (var connection = this.dataSource.getConnection()) {
            final boolean previousAutoCommit = connection.getAutoCommit();
            final boolean previousReadOnly = connection.isReadOnly();
            final int previousIsolation = connection.getTransactionIsolation();
            Throwable failure = null;

            try {
                if (options.isolation().shouldApply() && previousIsolation != options.isolation().jdbcLevel()) {
                    connection.setTransactionIsolation(options.isolation().jdbcLevel());
                }
                if (previousReadOnly != options.readOnly()) {
                    connection.setReadOnly(options.readOnly());
                }
                connection.setAutoCommit(false);

                final T result = transaction.execute(connection);
                connection.commit();
                return result;
            } catch (final Throwable exception) {
                failure = exception;
                rollback(connection, failure);
                throw wrapTransactionFailure(failure);
            } finally {
                restoreConnectionState(connection, previousAutoCommit, previousReadOnly, previousIsolation, failure);
            }
        } catch (final SQLException exception) {
            throw new DatabaseException("Failed to execute database transaction.", exception);
        }
    }

    private static void rollback(final java.sql.Connection connection, final Throwable failure) {
        try {
            connection.rollback();
        } catch (final SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreConnectionState(
        final java.sql.Connection connection,
        final boolean previousAutoCommit,
        final boolean previousReadOnly,
        final int previousIsolation,
        final Throwable originalFailure
    ) {
        DatabaseException restoreFailure = null;
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (final SQLException exception) {
            restoreFailure = new DatabaseException("Failed to restore transaction auto-commit state.", exception);
        }
        try {
            if (connection.isReadOnly() != previousReadOnly) {
                connection.setReadOnly(previousReadOnly);
            }
        } catch (final SQLException exception) {
            restoreFailure = appendRestoreFailure(restoreFailure, "Failed to restore transaction read-only state.", exception);
        }
        try {
            if (connection.getTransactionIsolation() != previousIsolation) {
                connection.setTransactionIsolation(previousIsolation);
            }
        } catch (final SQLException exception) {
            restoreFailure = appendRestoreFailure(restoreFailure, "Failed to restore transaction isolation state.", exception);
        }

        if (restoreFailure != null) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(restoreFailure);
                return;
            }
            throw restoreFailure;
        }
    }

    private static DatabaseException appendRestoreFailure(final DatabaseException existing, final String message, final SQLException exception) {
        final DatabaseException failure = new DatabaseException(message, exception);
        if (existing == null) {
            return failure;
        }
        existing.addSuppressed(failure);
        return existing;
    }

    private static RuntimeException wrapTransactionFailure(final Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new DatabaseException("Failed to execute database transaction.", failure);
    }

    private void ensureOpen(final String operation) {
        if (this.closed.get()) {
            throw closedException(operation);
        }
    }

    private static DatabaseException closedException(final String operation) {
        return new DatabaseException("Cannot execute database " + operation + " because the database is closed.");
    }

    private static DatabaseException wrapFailure(final String operation, final Throwable exception) {
        if (exception instanceof DatabaseException databaseException) {
            return databaseException;
        }
        return new DatabaseException("Failed to execute database " + operation + '.', exception);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {

        T get() throws Exception;
    }
}
