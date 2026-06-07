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
    }

    @Test
    void buildRejectsInvalidLocationsAndPlaceholders() {
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().clearLocations().build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().clearLocations().enabled(false).addLocation(" ").build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().putPlaceholder("", "value").build());
        assertThrows(DatabaseException.class, () -> MigrationConfig.builder().putPlaceholder("key", null).build());
    }
}
