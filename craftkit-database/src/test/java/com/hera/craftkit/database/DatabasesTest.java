package com.hera.craftkit.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabasesTest {

    @Test
    void mysqlFactoryCreatesDatabaseWithOwnedExecutorWithoutConnectingImmediately() {
        final DatabaseConfig config = DatabaseConfig.builder()
            .host("127.0.0.1")
            .database("craftkit_test")
            .username("craftkit")
            .password("secret")
            .tablePrefix("ck_")
            .migration(MigrationConfig.builder().enabled(false).build())
            .pool(PoolConfig.builder().maximumPoolSize(1).minimumIdle(0).build())
            .build();

        final Database database = assertDoesNotThrow(() -> Databases.mysql(config));
        try {
            assertFalse(database.isClosed());
            assertTrue(database.table("players").equals("ck_players"));
        } finally {
            database.close();
        }

        assertTrue(database.isClosed());
    }
}
