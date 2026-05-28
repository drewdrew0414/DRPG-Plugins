package com.drewdrew1.database;

import com.drewdrew1.api.Database;
import com.drewdrew1.api.Migration;
import com.drewdrew1.api.SqlBinder;
import com.drewdrew1.api.SqlMapper;
import com.drewdrew1.api.SqlWork;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SQLiteDatabase implements Database {
    private final String name;
    private final Path path;
    private final Logger logger;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Connection connection;

    SQLiteDatabase(String name, Path path, Logger logger) {
        this.name = Objects.requireNonNull(name, "name");
        this.path = Objects.requireNonNull(path, "path");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "DBManager-" + name);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public CompletableFuture<Void> execute(String sql) {
        return execute(sql, SqlBinder.none());
    }

    @Override
    public CompletableFuture<Void> execute(String sql, SqlBinder binder) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");

        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                statement.execute();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Integer> update(String sql) {
        return update(sql, SqlBinder.none());
    }

    @Override
    public CompletableFuture<Integer> update(String sql, SqlBinder binder) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");

        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<int[]> batch(String sql, Iterable<SqlBinder> binders) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binders, "binders");

        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (SqlBinder binder : binders) {
                    Objects.requireNonNull(binder, "binder").bind(statement);
                    statement.addBatch();
                    statement.clearParameters();
                }
                return statement.executeBatch();
            }
        });
    }

    @Override
    public <T> CompletableFuture<List<T>> query(String sql, SqlMapper<T> mapper) {
        return query(sql, SqlBinder.none(), mapper);
    }

    @Override
    public <T> CompletableFuture<List<T>> query(String sql, SqlBinder binder, SqlMapper<T> mapper) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        Objects.requireNonNull(mapper, "mapper");

        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);

                try (ResultSet resultSet = statement.executeQuery()) {
                    List<T> results = new ArrayList<>();
                    while (resultSet.next()) {
                        results.add(mapper.map(resultSet));
                    }
                    return results;
                }
            }
        });
    }

    @Override
    public <T> CompletableFuture<Optional<T>> queryOne(String sql, SqlMapper<T> mapper) {
        return queryOne(sql, SqlBinder.none(), mapper);
    }

    @Override
    public <T> CompletableFuture<Optional<T>> queryOne(String sql, SqlBinder binder, SqlMapper<T> mapper) {
        return query(sql, binder, mapper).thenApply(results -> results.stream().findFirst());
    }

    @Override
    public <T> CompletableFuture<T> transaction(SqlWork<T> work) {
        Objects.requireNonNull(work, "work");

        return submit(connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> schemaVersion() {
        return submit(this::readSchemaVersion);
    }

    @Override
    public CompletableFuture<Void> migrate(Collection<Migration> migrations) {
        Objects.requireNonNull(migrations, "migrations");
        List<Migration> orderedMigrations = migrations.stream()
                .sorted(Comparator.comparingInt(Migration::version))
                .toList();
        validateMigrations(orderedMigrations);

        return transaction(connection -> {
            int currentVersion = readSchemaVersion(connection);
            for (Migration migration : orderedMigrations) {
                if (migration.version() <= currentVersion) {
                    continue;
                }

                migration.work().execute(connection);
                setSchemaVersion(connection, migration.version());
                currentVersion = migration.version();
                logger.info("Applied database migration " + migration.version() + " for " + name + ": " + migration.description());
            }
            return null;
        });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to close database " + name + ".", exception);
            }
        }, executor).join();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private <T> CompletableFuture<T> submit(SqlWork<T> work) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database is closed: " + name));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.execute(connection());
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private Connection connection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        try {
            Files.createDirectories(path.getParent());
        } catch (IOException exception) {
            throw new SQLException("Failed to create database directory for " + name + ".", exception);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        configure(connection);
        return connection;
    }

    private void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
            statement.execute("PRAGMA journal_mode = WAL;");
            statement.execute("PRAGMA busy_timeout = 5000;");
        }
    }

    private int readSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version;")) {
            if (!resultSet.next()) {
                return 0;
            }
            return resultSet.getInt(1);
        }
    }

    private void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + version + ";");
        }
    }

    private void rollback(Connection connection, Exception cause) throws SQLException {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }

    private void validateMigrations(List<Migration> migrations) {
        Set<Integer> versions = new HashSet<>();
        for (Migration migration : migrations) {
            if (!versions.add(migration.version())) {
                throw new IllegalArgumentException("Duplicate migration version: " + migration.version());
            }
        }
    }
}
