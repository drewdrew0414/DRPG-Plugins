package com.drewdrew1.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleEvent;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.drewdrew1.api.Database;
import com.drewdrew1.api.DatabaseService;
import com.drewdrew1.event.DBManagerDatabaseEvent;
import com.drewdrew1.event.DBManagerDatabaseEvent.OperationType;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;

public final class SkriptIntegration {
    private static JavaPlugin plugin;
    private static DatabaseService databaseService;

    private SkriptIntegration() {
    }

    @SuppressWarnings({ "deprecation", "removal" })
    public static void register(JavaPlugin owner, DatabaseService service) {
        plugin = Objects.requireNonNull(owner, "owner");
        databaseService = Objects.requireNonNull(service, "service");

        Skript.registerAddon(owner);

        Skript.registerEffect(ExecuteEffect.class,
                "db[manager] execute %string% (in|on) [database] %string%",
                "db[manager] execute %string% (in|on) [database] %string% with [parameters] %objects%",
                "db[manager] execute %string% (in|on) [database] %string% named %string%",
                "db[manager] execute %string% (in|on) [database] %string% named %string% with [parameters] %objects%"
        );

        Skript.registerEffect(QueryEffect.class,
                "db[manager] query %string% (in|on) [database] %string%",
                "db[manager] query %string% (in|on) [database] %string% with [parameters] %objects%",
                "db[manager] query %string% (in|on) [database] %string% named %string%",
                "db[manager] query %string% (in|on) [database] %string% named %string% with [parameters] %objects%"
        );

        Skript.registerEvent("DBManager database operation complete",
                SimpleEvent.class,
                DBManagerDatabaseEvent.class,
                "[dbmanager] database operation complete",
                "[dbmanager] db operation complete"
        );

        Skript.registerExpression(OperationTypeExpression.class, String.class, ExpressionType.SIMPLE,
                "[the] dbmanager operation type"
        );
        Skript.registerExpression(DatabaseNameExpression.class, String.class, ExpressionType.SIMPLE,
                "[the] dbmanager database name"
        );
        Skript.registerExpression(OperationIdExpression.class, String.class, ExpressionType.SIMPLE,
                "[the] dbmanager operation id",
                "[the] dbmanager query id"
        );
        Skript.registerExpression(OperationSqlExpression.class, String.class, ExpressionType.SIMPLE,
                "[the] dbmanager sql"
        );
        Skript.registerExpression(QueryRowsExpression.class, String.class, ExpressionType.SIMPLE,
                "[the] dbmanager query rows",
                "[the] dbmanager result rows"
        );
        Skript.registerExpression(RowCountExpression.class, Number.class, ExpressionType.SIMPLE,
                "[the] dbmanager row count"
        );
        Skript.registerExpression(AffectedRowsExpression.class, Number.class, ExpressionType.SIMPLE,
                "[the] dbmanager affected rows"
        );
        Skript.registerExpression(ErrorExpression.class, String.class, ExpressionType.SIMPLE,
                "[the] dbmanager error"
        );

        owner.getLogger().info("Skript integration is enabled.");
    }

    public static final class ExecuteEffect extends DatabaseEffect {
        @Override
        protected void execute(Event event) {
            OperationInput input = input(event);
            if (input == null) {
                return;
            }

            database(input.databaseName())
                    .update(input.sql(), statement -> bind(statement, input.parameters()))
                    .whenComplete((affectedRows, throwable) -> fireOperationEvent(
                            OperationType.EXECUTE,
                            input,
                            List.of(),
                            affectedRows == null ? -1 : affectedRows,
                            throwable
                    ));
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager execute";
        }
    }

    public static final class QueryEffect extends DatabaseEffect {
        @Override
        protected void execute(Event event) {
            OperationInput input = input(event);
            if (input == null) {
                return;
            }

            database(input.databaseName())
                    .query(input.sql(), statement -> bind(statement, input.parameters()), SkriptIntegration::rowToJson)
                    .whenComplete((rows, throwable) -> fireOperationEvent(
                            OperationType.QUERY,
                            input,
                            rows == null ? List.of() : rows,
                            -1,
                            throwable
                    ));
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager query";
        }
    }

    public abstract static class DatabaseEffect extends Effect {
        private Expression<String> sqlExpression;
        private Expression<String> databaseExpression;
        private Expression<String> operationIdExpression;
        private Expression<?> parametersExpression;

        @Override
        @SuppressWarnings("unchecked")
        public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            sqlExpression = (Expression<String>) expressions[0];
            databaseExpression = (Expression<String>) expressions[1];

            if (matchedPattern == 1) {
                parametersExpression = expressions[2];
            } else if (matchedPattern == 2) {
                operationIdExpression = (Expression<String>) expressions[2];
            } else if (matchedPattern == 3) {
                operationIdExpression = (Expression<String>) expressions[2];
                parametersExpression = expressions[3];
            }

            return true;
        }

        protected OperationInput input(Event event) {
            String sql = sqlExpression.getSingle(event);
            String databaseName = databaseExpression.getSingle(event);
            if (sql == null || sql.isBlank() || databaseName == null || databaseName.isBlank()) {
                return null;
            }

            String operationId = "";
            if (operationIdExpression != null) {
                String parsedId = operationIdExpression.getSingle(event);
                if (parsedId != null) {
                    operationId = parsedId;
                }
            }

            Object[] parameters = parametersExpression == null
                    ? new Object[0]
                    : parametersExpression.getArray(event);

            return new OperationInput(databaseName, operationId, sql, parameters);
        }
    }

    public static final class OperationTypeExpression extends DatabaseEventExpression<String> {
        @Override
        protected String[] get(Event event) {
            DBManagerDatabaseEvent databaseEvent = databaseEvent(event);
            return databaseEvent == null
                    ? new String[0]
                    : new String[] { databaseEvent.operationType().name().toLowerCase() };
        }

        @Override
        public Class<? extends String> getReturnType() {
            return String.class;
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager operation type";
        }
    }

    public static final class DatabaseNameExpression extends DatabaseEventExpression<String> {
        @Override
        protected String[] get(Event event) {
            DBManagerDatabaseEvent databaseEvent = databaseEvent(event);
            return stringValue(databaseEvent == null ? "" : databaseEvent.databaseName());
        }

        @Override
        public Class<? extends String> getReturnType() {
            return String.class;
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager database name";
        }
    }

    public static final class OperationIdExpression extends DatabaseEventExpression<String> {
        @Override
        protected String[] get(Event event) {
            DBManagerDatabaseEvent databaseEvent = databaseEvent(event);
            return stringValue(databaseEvent == null ? "" : databaseEvent.operationId());
        }

        @Override
        public Class<? extends String> getReturnType() {
            return String.class;
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager operation id";
        }
    }

    public static final class OperationSqlExpression extends DatabaseEventExpression<String> {
        @Override
        protected String[] get(Event event) {
            DBManagerDatabaseEvent databaseEvent = databaseEvent(event);
            return stringValue(databaseEvent == null ? "" : databaseEvent.sql());
        }

        @Override
        public Class<? extends String> getReturnType() {
            return String.class;
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager sql";
        }
    }

    public static final class QueryRowsExpression extends DatabaseEventExpression<String> {
        @Override
        protected String[] get(Event event) {
            DBManagerDatabaseEvent databaseEvent = databaseEvent(event);
            return databaseEvent == null
                    ? new String[0]
                    : databaseEvent.rows().toArray(String[]::new);
        }

        @Override
        public boolean isSingle() {
            return false;
        }

        @Override
        public Class<? extends String> getReturnType() {
            return String.class;
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager query rows";
        }
    }

    public static final class RowCountExpression extends DatabaseEventExpression<Number> {
        @Override
        protected Number[] get(Event event) {
            DBManagerDatabaseEvent databaseEvent = databaseEvent(event);
            return new Number[] { databaseEvent == null ? 0 : databaseEvent.rows().size() };
        }

        @Override
        public Class<? extends Number> getReturnType() {
            return Number.class;
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager row count";
        }
    }

    public static final class AffectedRowsExpression extends DatabaseEventExpression<Number> {
        @Override
        protected Number[] get(Event event) {
            DBManagerDatabaseEvent databaseEvent = databaseEvent(event);
            return new Number[] { databaseEvent == null ? -1 : databaseEvent.affectedRows() };
        }

        @Override
        public Class<? extends Number> getReturnType() {
            return Number.class;
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager affected rows";
        }
    }

    public static final class ErrorExpression extends DatabaseEventExpression<String> {
        @Override
        protected String[] get(Event event) {
            DBManagerDatabaseEvent databaseEvent = databaseEvent(event);
            return stringValue(databaseEvent == null ? "" : databaseEvent.error());
        }

        @Override
        public Class<? extends String> getReturnType() {
            return String.class;
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "dbmanager error";
        }
    }

    public abstract static class DatabaseEventExpression<T> extends SimpleExpression<T> {
        @Override
        public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            return true;
        }

        @Override
        public boolean isSingle() {
            return true;
        }
    }

    private static Database database(String name) {
        return databaseService.database(name);
    }

    private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private static String rowToJson(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        StringBuilder json = new StringBuilder("{");

        for (int column = 1; column <= metadata.getColumnCount(); column++) {
            if (column > 1) {
                json.append(',');
            }

            json.append('"')
                    .append(escapeJson(metadata.getColumnLabel(column)))
                    .append("\":");
            appendJsonValue(json, resultSet.getObject(column));
        }

        return json.append('}').toString();
    }

    private static void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
            return;
        }

        if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
            return;
        }

        json.append('"').append(escapeJson(String.valueOf(value))).append('"');
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String[] stringValue(String value) {
        return value.isBlank() ? new String[0] : new String[] { value };
    }

    private static DBManagerDatabaseEvent databaseEvent(Event event) {
        return event instanceof DBManagerDatabaseEvent databaseEvent ? databaseEvent : null;
    }

    private static void fireOperationEvent(
            OperationType operationType,
            OperationInput input,
            List<String> rows,
            int affectedRows,
            Throwable throwable
    ) {
        Throwable unwrapped = unwrap(throwable);
        String error = unwrapped == null ? "" : unwrapped.getMessage();
        DBManagerDatabaseEvent event = new DBManagerDatabaseEvent(
                operationType,
                input.databaseName(),
                input.operationId(),
                input.sql(),
                rows,
                affectedRows,
                error
        );

        if (!plugin.isEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(event));
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }

        return throwable;
    }

    private record OperationInput(String databaseName, String operationId, String sql, Object[] parameters) {
    }
}
