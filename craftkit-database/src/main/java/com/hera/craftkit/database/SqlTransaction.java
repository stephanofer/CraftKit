package com.hera.craftkit.database;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlTransaction<T> {

    T execute(Connection connection) throws SQLException;
}
