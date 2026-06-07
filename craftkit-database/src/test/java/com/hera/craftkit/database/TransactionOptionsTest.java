package com.hera.craftkit.database;

import java.sql.Connection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TransactionOptionsTest {

    @Test
    void defaultsDoNotForceIsolationOrReadOnly() {
        final TransactionOptions options = TransactionOptions.defaults();

        assertEquals(TransactionIsolation.DEFAULT, options.isolation());
        assertFalse(options.readOnly());
        assertFalse(options.isolation().shouldApply());
    }

    @Test
    void namedFactoriesMapToJdbcIsolationLevels() {
        assertEquals(Connection.TRANSACTION_READ_UNCOMMITTED, TransactionOptions.readUncommitted().isolation().jdbcLevel());
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, TransactionOptions.readCommitted().isolation().jdbcLevel());
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, TransactionOptions.repeatableRead().isolation().jdbcLevel());
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, TransactionOptions.serializable().isolation().jdbcLevel());
    }

    @Test
    void readOnlyFactorySetsIsolationAndReadOnlyFlag() {
        final TransactionOptions options = TransactionOptions.readOnly(TransactionIsolation.READ_COMMITTED);

        assertEquals(TransactionIsolation.READ_COMMITTED, options.isolation());
        assertTrue(options.readOnly());
    }

    @Test
    void builderRejectsNullIsolation() {
        assertThrows(NullPointerException.class, () -> TransactionOptions.builder().isolation(null).build());
    }
}
