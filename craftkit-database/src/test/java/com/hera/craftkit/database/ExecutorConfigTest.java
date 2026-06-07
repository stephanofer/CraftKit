package com.hera.craftkit.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExecutorConfigTest {

    @Test
    void buildUsesExpectedDefaults() {
        final ExecutorConfig config = ExecutorConfig.builder().build();

        assertEquals(10, config.threadCount());
        assertEquals(ExecutorConfig.DEFAULT_THREAD_NAME_PREFIX, config.threadNamePrefix());
        assertTrue(config.daemon());
    }

    @Test
    void buildRejectsInvalidValues() {
        assertThrows(DatabaseException.class, () -> ExecutorConfig.builder().threadCount(0).build());
        assertThrows(DatabaseException.class, () -> ExecutorConfig.builder().threadNamePrefix(" ").build());
        assertThrows(DatabaseException.class, () -> ExecutorConfig.builder().shutdownTimeoutMillis(0).build());
        assertThrows(DatabaseException.class, () -> ExecutorConfig.builder().shutdownTimeoutMillis(-1).build());
    }
}
