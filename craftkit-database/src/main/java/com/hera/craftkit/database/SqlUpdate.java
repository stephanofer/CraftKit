package com.hera.craftkit.database;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlUpdate {

    int execute(Connection connection) throws SQLException;
}
