package com.hera.craftkit.database.internal;

interface DatabaseMigrator {

    boolean isEnabled();

    void migrate();
}
