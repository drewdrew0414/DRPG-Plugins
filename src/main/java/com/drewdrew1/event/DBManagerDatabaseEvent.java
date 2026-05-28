package com.drewdrew1.event;

import java.util.List;
import java.util.Objects;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DBManagerDatabaseEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OperationType operationType;
    private final String databaseName;
    private final String operationId;
    private final String sql;
    private final List<String> rows;
    private final int affectedRows;
    private final String error;

    public DBManagerDatabaseEvent(
            OperationType operationType,
            String databaseName,
            String operationId,
            String sql,
            List<String> rows,
            int affectedRows,
            String error
    ) {
        this.operationType = Objects.requireNonNull(operationType, "operationType");
        this.databaseName = Objects.requireNonNull(databaseName, "databaseName");
        this.operationId = operationId == null ? "" : operationId;
        this.sql = Objects.requireNonNull(sql, "sql");
        this.rows = List.copyOf(rows);
        this.affectedRows = affectedRows;
        this.error = error == null ? "" : error;
    }

    public OperationType operationType() {
        return operationType;
    }

    public String databaseName() {
        return databaseName;
    }

    public String operationId() {
        return operationId;
    }

    public String sql() {
        return sql;
    }

    public List<String> rows() {
        return rows;
    }

    public int affectedRows() {
        return affectedRows;
    }

    public String error() {
        return error;
    }

    public boolean successful() {
        return error.isBlank();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum OperationType {
        EXECUTE,
        QUERY
    }
}
