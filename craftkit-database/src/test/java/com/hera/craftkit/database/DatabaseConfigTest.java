package com.hera.craftkit.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabaseConfigTest {

    @Test
    void buildUsesPoolMaximumSizeForDefaultExecutorThreadCount() {
        final DatabaseConfig config = DatabaseConfig.builder()
            .host("localhost")
            .database("craftkit")
            .username("hera")
            .password("secret")
            .pool(PoolConfig.builder().maximumPoolSize(3).build())
            .build();

        assertEquals(3, config.executor().threadCount());
        assertEquals("", config.tablePrefix());
        assertNull(config.driverClassName());
    }

    @Test
    void buildTrimsOptionalDriverClassName() {
        final DatabaseConfig config = DatabaseConfig.builder()
            .host("localhost")
            .database("craftkit")
            .username("hera")
            .driverClassName(" com.mysql.cj.jdbc.Driver ")
            .build();

        assertEquals("com.mysql.cj.jdbc.Driver", config.driverClassName());
    }

    @Test
    void buildRejectsInvalidCoreValues() {
        assertThrows(DatabaseException.class, () -> DatabaseConfig.builder()
            .host(" ")
            .database("craftkit")
            .username("hera")
            .build());

        assertThrows(DatabaseException.class, () -> DatabaseConfig.builder()
            .host("localhost")
            .database("craftkit")
            .username("hera")
            .port(0)
            .build());

        assertThrows(DatabaseException.class, () -> DatabaseConfig.builder()
            .host("localhost")
            .database("craftkit")
            .username("hera")
            .tablePrefix("bad-prefix")
            .build());
    }

    @Test
    void buildRejectsInvalidJdbcProperties() {
        assertThrows(DatabaseException.class, () -> DatabaseConfig.builder()
            .host("localhost")
            .database("craftkit")
            .username("hera")
            .putJdbcProperty("", "value")
            .build());

        assertThrows(DatabaseException.class, () -> DatabaseConfig.builder()
            .host("localhost")
            .database("craftkit")
            .username("hera")
            .putJdbcProperty("socketTimeout", null)
            .build());
    }

    @Test
    void toStringDoesNotExposePassword() {
        final DatabaseConfig config = DatabaseConfig.builder()
            .host("localhost")
            .database("craftkit")
            .username("hera")
            .password("super-secret")
            .build();

        final String rendered = config.toString();

        assertTrue(rendered.contains("password=<hidden>"));
        assertFalse(rendered.contains("super-secret"));
    }

    @Test
    void buildRejectsPrefixesThatBreakFlywayHistoryTableLength() {
        assertThrows(DatabaseException.class, () -> DatabaseConfig.builder()
            .host("localhost")
            .database("craftkit")
            .username("hera")
            .tablePrefix("a".repeat(44))
            .build());
    }
}
