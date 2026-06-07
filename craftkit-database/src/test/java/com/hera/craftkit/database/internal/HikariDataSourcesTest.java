package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.PoolConfig;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HikariDataSourcesTest {

    @Test
    void buildConfigUsesExpectedDefaultsAndOverrides() {
        final DatabaseConfig config = DatabaseConfig.builder()
            .host("db.example.net")
            .port(3307)
            .database("craftkit")
            .username("hera")
            .password("secret")
            .pool(PoolConfig.builder().maximumPoolSize(12).minimumIdle(2).build())
            .putJdbcProperty("cachePrepStmts", "false")
            .putJdbcProperty("socketTimeout", "4000")
            .build();

        final HikariConfig hikariConfig = HikariDataSources.buildConfig(config);

        assertEquals("jdbc:mysql://db.example.net:3307/craftkit", hikariConfig.getJdbcUrl());
        assertEquals("hera", hikariConfig.getUsername());
        assertEquals("secret", hikariConfig.getPassword());
        assertEquals(12, hikariConfig.getMaximumPoolSize());
        assertEquals(2, hikariConfig.getMinimumIdle());
        assertEquals("false", hikariConfig.getDataSourceProperties().getProperty("cachePrepStmts"));
        assertEquals("4000", hikariConfig.getDataSourceProperties().getProperty("socketTimeout"));
        assertEquals("true", hikariConfig.getDataSourceProperties().getProperty("rewriteBatchedStatements"));
        assertEquals(-1L, hikariConfig.getInitializationFailTimeout());
    }
}
