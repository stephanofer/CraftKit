package com.hera.craftkit.database;

import com.hera.craftkit.database.internal.DatabaseExecutors;
import com.hera.craftkit.database.internal.FlywayMigrator;
import com.hera.craftkit.database.internal.HikariDataSources;
import com.hera.craftkit.database.internal.HikariDatabase;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public final class Databases {

    private Databases() {
    }

    public static Database mysql(final DatabaseConfig config) {
        final DatabaseConfig resolvedConfig = Objects.requireNonNull(config, "Database config must not be null.");

        final ExecutorService executorService = DatabaseExecutors.createExecutor(resolvedConfig.executor());
        HikariDataSource dataSource = null;
        try {
            dataSource = HikariDataSources.create(resolvedConfig);
            return new HikariDatabase(
                dataSource,
                dataSource,
                resolvedConfig.tablePrefix(),
                executorService,
                executorService,
                resolvedConfig.executor(),
                new FlywayMigrator(dataSource, resolvedConfig.migration(), resolvedConfig.tablePrefix())
            );
        } catch (final RuntimeException exception) {
            if (dataSource != null) {
                closeQuietly(dataSource, exception);
            }
            DatabaseExecutors.shutdownQuietly(executorService, resolvedConfig.executor(), exception);
            throw exception;
        }
    }

    public static Database mysql(final DatabaseConfig config, final Executor executor) {
        final DatabaseConfig resolvedConfig = Objects.requireNonNull(config, "Database config must not be null.");
        final Executor resolvedExecutor = Objects.requireNonNull(executor, "Executor must not be null.");

        final HikariDataSource dataSource = HikariDataSources.create(resolvedConfig);
        try {
            return new HikariDatabase(
                dataSource,
                dataSource,
                resolvedConfig.tablePrefix(),
                resolvedExecutor,
                null,
                resolvedConfig.executor(),
                new FlywayMigrator(dataSource, resolvedConfig.migration(), resolvedConfig.tablePrefix())
            );
        } catch (final RuntimeException exception) {
            closeQuietly(dataSource, exception);
            throw exception;
        }
    }

    private static void closeQuietly(final HikariDataSource dataSource, final RuntimeException failure) {
        try {
            dataSource.close();
        } catch (final RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
