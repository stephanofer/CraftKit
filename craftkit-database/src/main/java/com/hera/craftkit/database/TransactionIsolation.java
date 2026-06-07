package com.hera.craftkit.database;

import java.sql.Connection;

public enum TransactionIsolation {

    DEFAULT(null),
    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final Integer jdbcLevel;

    TransactionIsolation(final Integer jdbcLevel) {
        this.jdbcLevel = jdbcLevel;
    }

    public Integer jdbcLevel() {
        return this.jdbcLevel;
    }

    public boolean shouldApply() {
        return this.jdbcLevel != null;
    }
}
