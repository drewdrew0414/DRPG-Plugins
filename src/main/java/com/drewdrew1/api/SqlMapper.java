package com.drewdrew1.api;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlMapper<T> {
    T map(ResultSet resultSet) throws SQLException;
}
