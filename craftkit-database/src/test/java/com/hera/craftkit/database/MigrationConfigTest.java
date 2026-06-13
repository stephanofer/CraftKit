package com.hera.craftkit.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MigrationConfigTest {

    @Test
    void buildUsesExpectedDefaults() {
        final MigrationConfig config = MigrationConfig.builder().build();

        assertTrue(config.enabled());
        assertEquals(1, config.locations().size());
        assertEquals(MigrationConfig.DEFAULT_LOCATION, config.locations().getFirst());
        assertTrue(config.validateOnMigrate());
        assertTrue(config.cleanDisabled());
        assertEquals(ExistingSchemaStrategy.FAIL, config.existingSchemaStrategy());
        assertEquals("0", config.baselineVersion());
        assertEquals("CraftKit baseline", config.baselineDescription());
        assertTrue(config.classLoader() != null);
    }

    @Test
    void acceptsExplicitMigrationClassLoader() {
        final ClassLoader classLoader = new ClassLoader(null) {
        };

        final MigrationConfig config = MigrationConfig.builder()
            .classLoader(classLoader)
            .build();

        assertEquals(classLoader, config.classLoader());
    }

    @Test
    void sharedDatabaseDefaultsBaselineAtZeroForSharedSchemas() {
        final MigrationConfig config = MigrationConfig.sharedDatabaseDefaults();

        assertEquals(ExistingSchemaStrategy.BASELINE_AT_ZERO, config.existingSchemaStrategy());
        assertEquals("0", config.baselineVersion());
    }

    @Test
    void buildRejectsInvalidLocationsAndPlaceholders() {
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().clearLocations().build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().clearLocations().enabled(false).addLocation(" ").build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().putPlaceholder("", "value").build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().putPlaceholder("key", null).build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().baselineVersion(" ").build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().baselineVersion("one").build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().baselineDescription(" ").build());
    }
}
