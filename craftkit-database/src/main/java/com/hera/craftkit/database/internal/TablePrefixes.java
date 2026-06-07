package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseException;

import java.util.Objects;
import java.util.regex.Pattern;

public final class TablePrefixes {

    static final int MAX_PREFIX_LENGTH = 48;
    static final int MAX_TABLE_NAME_LENGTH = 48;
    static final int MAX_COMPOSED_TABLE_LENGTH = 64;

    private static final Pattern PREFIX_PATTERN = Pattern.compile("[A-Za-z0-9_]*");
    private static final Pattern TABLE_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private TablePrefixes() {
    }

    public static String validatePrefix(final String prefix) {
        final String resolvedPrefix = Objects.requireNonNull(prefix, "Table prefix must not be null.");
        if (resolvedPrefix.length() > MAX_PREFIX_LENGTH) {
            throw new DatabaseException("Table prefix must be at most " + MAX_PREFIX_LENGTH + " characters.");
        }
        if (!PREFIX_PATTERN.matcher(resolvedPrefix).matches()) {
            throw new DatabaseException("Table prefix may contain only letters, digits, and underscores.");
        }
        return resolvedPrefix;
    }

    public static String validateTableName(final String tableName) {
        final String resolvedTableName = Objects.requireNonNull(tableName, "Table name must not be null.");
        if (resolvedTableName.isEmpty()) {
            throw new DatabaseException("Table name must not be empty.");
        }
        if (resolvedTableName.length() > MAX_TABLE_NAME_LENGTH) {
            throw new DatabaseException("Table name must be at most " + MAX_TABLE_NAME_LENGTH + " characters.");
        }
        if (!TABLE_PATTERN.matcher(resolvedTableName).matches()) {
            throw new DatabaseException("Table name may contain only letters, digits, and underscores.");
        }
        return resolvedTableName;
    }

    public static String table(final String prefix, final String tableName) {
        final String resolvedPrefix = validatePrefix(prefix);
        final String resolvedTableName = validateTableName(tableName);
        final String composed = resolvedPrefix + resolvedTableName;
        if (composed.length() > MAX_COMPOSED_TABLE_LENGTH) {
            throw new DatabaseException("Composed table name must be at most " + MAX_COMPOSED_TABLE_LENGTH + " characters.");
        }
        return composed;
    }
}
