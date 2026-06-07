package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseException;
import com.hera.craftkit.database.MigrationConfig;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FlywayMigratorTest {

    @Test
    void migratorResolvesLocationsPlaceholdersAndHistoryTable() {
        final MigrationConfig config = MigrationConfig.builder()
            .clearLocations()
            .addLocation("classpath:db/mysql")
            .putPlaceholder("schema", "craftkit")
            .build();

        final FlywayMigrator migrator = new FlywayMigrator(new FailingDataSource(), config, "ck_");

        assertEquals("classpath:db/mysql", migrator.locations().getFirst());
        assertEquals("craftkit", migrator.placeholders().get("schema"));
        assertEquals("ck_", migrator.placeholders().get("tablePrefix"));
        assertEquals("ck_flyway_schema_history", migrator.historyTable());
        assertTrue(migrator.createFlyway().getConfiguration().isCleanDisabled());
    }

    @Test
    void migrateWrapsFailuresWithoutSecrets() {
        final FlywayMigrator migrator = new FlywayMigrator(new FailingDataSource(), MigrationConfig.builder().build(), "ck_");

        final DatabaseException exception = assertThrows(DatabaseException.class, migrator::migrate);

        assertTrue(exception.getMessage().contains("Flyway migrations"));
        assertTrue(exception.getCause() != null);
    }

    private static final class FailingDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("boom");
        }

        @Override
        public Connection getConnection(final String username, final String password) throws SQLException {
            throw new SQLException("boom");
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
