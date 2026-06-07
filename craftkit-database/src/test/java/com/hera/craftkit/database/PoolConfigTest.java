package com.hera.craftkit.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PoolConfigTest {

    @Test
    void buildUsesExpectedDefaults() {
        final PoolConfig config = PoolConfig.builder().build();

        assertEquals(PoolConfig.DEFAULT_POOL_NAME, config.poolName());
        assertEquals(10, config.maximumPoolSize());
        assertNull(config.minimumIdle());
        assertTrue(config.autoCommit());
    }

    @Test
    void buildRejectsInvalidSizingAndTimeouts() {
        assertThrows(DatabaseException.class, () -> PoolConfig.builder().maximumPoolSize(0).build());
        assertThrows(DatabaseException.class, () -> PoolConfig.builder().maximumPoolSize(2).minimumIdle(3).build());
        assertThrows(DatabaseException.class, () -> PoolConfig.builder().connectionTimeoutMillis(0).build());
        assertThrows(DatabaseException.class, () -> PoolConfig.builder().validationTimeoutMillis(0).build());
        assertThrows(DatabaseException.class, () -> PoolConfig.builder().idleTimeoutMillis(-1).build());
        assertThrows(DatabaseException.class, () -> PoolConfig.builder().maxLifetimeMillis(-1).build());
    }
}
