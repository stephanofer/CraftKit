package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TablePrefixesTest {

    @Test
    void tableComposesValidatedPrefixAndName() {
        assertEquals("ck_users", TablePrefixes.table("ck_", "users"));
        assertEquals("users", TablePrefixes.table("", "users"));
    }

    @Test
    void validateRejectsUnsafeValues() {
        assertThrows(DatabaseException.class, () -> TablePrefixes.validatePrefix("bad-prefix"));
        assertThrows(DatabaseException.class, () -> TablePrefixes.validatePrefix("bad prefix"));
        assertThrows(DatabaseException.class, () -> TablePrefixes.validateTableName(""));
        assertThrows(DatabaseException.class, () -> TablePrefixes.validateTableName("bad.name"));
    }

    @Test
    void tableRejectsOverlongComposedIdentifier() {
        final String prefix = "a".repeat(40);
        final String name = "b".repeat(25);
        assertThrows(DatabaseException.class, () -> TablePrefixes.table(prefix, name));
    }
}
