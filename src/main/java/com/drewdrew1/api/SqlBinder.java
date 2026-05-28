package com.drewdrew1.api;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlBinder {
    void bind(PreparedStatement statement) throws SQLException;

    static SqlBinder none() {
        return statement -> {
        };
    }
}
