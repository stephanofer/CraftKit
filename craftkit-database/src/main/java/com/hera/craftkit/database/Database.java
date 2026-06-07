package com.hera.craftkit.database;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;

public interface Database extends AutoCloseable {

    CompletableFuture<Void> migrate();

    <T> CompletableFuture<T> query(SqlQuery<T> query);

    CompletableFuture<Integer> update(SqlUpdate update);

    CompletableFuture<Void> execute(SqlOperation operation);

    <T> CompletableFuture<T> transaction(SqlTransaction<T> transaction);

    <T> CompletableFuture<T> transaction(TransactionOptions options, SqlTransaction<T> transaction);

    DataSource dataSource();

    String tablePrefix();

    String table(String name);

    boolean isClosed();

    @Override
    void close();
}
