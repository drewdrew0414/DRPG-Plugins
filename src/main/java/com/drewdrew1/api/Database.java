package com.drewdrew1.api;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Database extends AutoCloseable {
    String name();

    Path path();

    CompletableFuture<Void> execute(String sql);

    CompletableFuture<Void> execute(String sql, SqlBinder binder);

    CompletableFuture<Integer> update(String sql);

    CompletableFuture<Integer> update(String sql, SqlBinder binder);

    CompletableFuture<int[]> batch(String sql, Iterable<SqlBinder> binders);

    <T> CompletableFuture<List<T>> query(String sql, SqlMapper<T> mapper);

    <T> CompletableFuture<List<T>> query(String sql, SqlBinder binder, SqlMapper<T> mapper);

    <T> CompletableFuture<Optional<T>> queryOne(String sql, SqlMapper<T> mapper);

    <T> CompletableFuture<Optional<T>> queryOne(String sql, SqlBinder binder, SqlMapper<T> mapper);

    <T> CompletableFuture<T> transaction(SqlWork<T> work);

    CompletableFuture<Integer> schemaVersion();

    CompletableFuture<Void> migrate(Collection<Migration> migrations);

    @Override
    void close();
}
