package com.drewdrew1.api;

import java.sql.Statement;
import java.util.Objects;

public record Migration(int version, String description, SqlWork<Void> work) {
    public Migration {
        if (version <= 0) {
            throw new IllegalArgumentException("Migration version must be greater than zero.");
        }

        description = Objects.requireNonNull(description, "description");
        work = Objects.requireNonNull(work, "work");
    }

    public static Migration sql(int version, String description, String sql) {
        Objects.requireNonNull(sql, "sql");
        return new Migration(version, description, connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }
}
