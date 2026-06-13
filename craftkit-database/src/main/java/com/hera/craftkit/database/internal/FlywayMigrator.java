package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseException;
import com.hera.craftkit.database.ExistingSchemaStrategy;
import com.hera.craftkit.database.MigrationConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FlywayMigrator implements DatabaseMigrator {

    private static final String FLYWAY_HISTORY_TABLE_NAME = "flyway_schema_history";

    private final DataSource dataSource;
    private final MigrationConfig config;
    private final String tablePrefix;
    private final ClassLoader classLoader;

    public FlywayMigrator(final DataSource dataSource, final MigrationConfig config, final String tablePrefix) {
        this(dataSource, config, tablePrefix, migrationClassLoader(config));
    }

    public FlywayMigrator(final DataSource dataSource, final MigrationConfig config, final String tablePrefix, final ClassLoader classLoader) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null.");
        this.config = Objects.requireNonNull(config, "Migration config must not be null.");
        this.tablePrefix = TablePrefixes.validatePrefix(Objects.requireNonNull(tablePrefix, "Table prefix must not be null."));
        this.classLoader = Objects.requireNonNull(classLoader, "Migration class loader must not be null.");
    }

    private static ClassLoader migrationClassLoader(final MigrationConfig config) {
        return Objects.requireNonNull(config, "Migration config must not be null.").classLoader();
    }

    public boolean isEnabled() {
        return this.config.enabled();
    }

    @Override
    public void migrate() {
        if (!this.config.enabled()) {
            return;
        }
        try {
            this.createFlyway().migrate();
        } catch (final RuntimeException exception) {
            throw new DatabaseException("Failed to run Flyway migrations.", exception);
        }
    }

    List<String> locations() {
        return this.config.locations();
    }

    Map<String, String> placeholders() {
        final Map<String, String> placeholders = new LinkedHashMap<>(this.config.placeholders());
        placeholders.put("tablePrefix", this.tablePrefix);
        return Map.copyOf(placeholders);
    }

    ClassLoader classLoader() {
        return this.classLoader;
    }

    String historyTable() {
        return TablePrefixes.table(this.tablePrefix, FLYWAY_HISTORY_TABLE_NAME);
    }

    boolean baselineOnMigrate() {
        return this.config.baselineOnMigrate() || this.config.existingSchemaStrategy() != ExistingSchemaStrategy.FAIL;
    }

    String baselineVersion() {
        if (this.config.existingSchemaStrategy() == ExistingSchemaStrategy.BASELINE_AT_ZERO) {
            return "0";
        }
        return this.config.baselineVersion();
    }

    Flyway createFlyway() {
        return Flyway.configure(this.classLoader)
            .dataSource(this.dataSource)
            .locations(this.config.locations().toArray(String[]::new))
            .baselineOnMigrate(this.baselineOnMigrate())
            .baselineVersion(MigrationVersion.fromVersion(this.baselineVersion()))
            .baselineDescription(this.config.baselineDescription())
            .validateOnMigrate(this.config.validateOnMigrate())
            .cleanDisabled(this.config.cleanDisabled())
            .placeholders(this.placeholders())
            .table(this.historyTable())
            .load();
    }
}
