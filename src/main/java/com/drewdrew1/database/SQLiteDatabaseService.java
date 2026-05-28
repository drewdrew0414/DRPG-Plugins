package com.drewdrew1.database;

import com.drewdrew1.api.Database;
import com.drewdrew1.api.DatabaseService;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public final class SQLiteDatabaseService implements DatabaseService {
    private final Path databaseDirectory;
    private final Logger logger;
    private final ConcurrentMap<String, SQLiteDatabase> databases = new ConcurrentHashMap<>();

    public SQLiteDatabaseService(JavaPlugin plugin) {
        this(
                Objects.requireNonNull(plugin, "plugin").getDataFolder().toPath().resolve("databases"),
                plugin.getLogger()
        );
    }

    public SQLiteDatabaseService(Path databaseDirectory, Logger logger) {
        this.databaseDirectory = Objects.requireNonNull(databaseDirectory, "databaseDirectory").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Database database(String name) {
        String normalizedName = normalizeDatabaseName(name);
        return databases.computeIfAbsent(normalizedName, key -> new SQLiteDatabase(
                key,
                databaseDirectory.resolve(key + ".db").normalize(),
                logger
        ));
    }

    @Override
    public Path databaseDirectory() {
        return databaseDirectory;
    }

    @Override
    public Collection<String> openDatabaseNames() {
        return Collections.unmodifiableSet(databases.keySet());
    }

    @Override
    public void close() {
        databases.values().forEach(SQLiteDatabase::close);
        databases.clear();
    }

    private String normalizeDatabaseName(String name) {
        String trimmedName = Objects.requireNonNull(name, "name").trim();
        if (trimmedName.endsWith(".db")) {
            trimmedName = trimmedName.substring(0, trimmedName.length() - 3);
        }

        if (trimmedName.isBlank()) {
            throw new IllegalArgumentException("Database name cannot be blank.");
        }

        if (!trimmedName.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Database name can only contain letters, numbers, '_', '-', and '.'.");
        }

        Path resolvedPath = databaseDirectory.resolve(trimmedName + ".db").normalize();
        if (!resolvedPath.startsWith(databaseDirectory)) {
            throw new IllegalArgumentException("Database name cannot escape the database directory.");
        }

        return trimmedName;
    }
}
