package com.hera.craftkit.database;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;

public interface Database extends AutoCloseable {

    CompletableFuture<Void> migrate();

    <T> CompletableFuture<T> query(SqlQuery<T> query);

    CompletableFuture<Integer> update(SqlUpdate update);

    CompletableFuture<Void> execute(SqlOperation operation);

    DataSource dataSource();

    String tablePrefix();

    String table(String name);

    boolean isClosed();

    @Override
    void close();
}
