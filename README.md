# DBManager

SQLite service plugin for Paper plugins.

## Using DBManager from another plugin

Add DBManager as a compile-only dependency in your plugin project, then declare a hard plugin dependency.

```yaml
depend: [DBManager]
```

Get the service from Bukkit's service manager:

```java
RegisteredServiceProvider<DatabaseService> provider =
        Bukkit.getServicesManager().getRegistration(DatabaseService.class);

if (provider == null) {
    throw new IllegalStateException("DBManager is not loaded.");
}

DatabaseService databaseService = provider.getProvider();
Database database = databaseService.database("rpg");
```

Create or migrate schema:

```java
database.migrate(List.of(
        Migration.sql(1, "create players table", """
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                );
                """)
));
```

Run queries asynchronously:

```java
database.update(
        "INSERT OR REPLACE INTO players(uuid, name) VALUES(?, ?)",
        statement -> {
            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, player.getName());
        }
);
```

Read data:

```java
database.queryOne(
        "SELECT name FROM players WHERE uuid = ?",
        statement -> statement.setString(1, uuid.toString()),
        resultSet -> resultSet.getString("name")
).thenAccept(name -> name.ifPresent(playerName -> {
    // Use the result back on the server thread if you need Bukkit API calls.
}));
```

Use transactions for multiple writes that must succeed together:

```java
database.transaction(connection -> {
    try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE players SET name = ? WHERE uuid = ?"
    )) {
        statement.setString(1, player.getName());
        statement.setString(2, player.getUniqueId().toString());
        statement.executeUpdate();
    }
    return null;
});
```

Database files are stored under `plugins/DBManager/databases`.

## Skript

DBManager also registers Skript syntax when the Skript plugin is installed.

Run SQL without parameters:

```vb
dbmanager execute "CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL)" in database "rpg" named "create_players"
```

Run SQL with prepared statement parameters:

```vb
dbmanager execute "INSERT OR REPLACE INTO players(uuid, name) VALUES(?, ?)" in database "rpg" named "save_player" with parameters "%uuid of player%" and name of player
```

Run an async query:

```vb
dbmanager query "SELECT uuid, name FROM players" in database "rpg" named "load_players"
```

Handle completion:

```vb
on dbmanager database operation complete:
    if dbmanager error is set:
        broadcast "DB error: %dbmanager error%"
        stop

    if dbmanager operation id is "load_players":
        loop dbmanager query rows:
            broadcast loop-value
```

Available Skript expressions inside `on dbmanager database operation complete`:

```vb
dbmanager operation type
dbmanager database name
dbmanager operation id
dbmanager query id
dbmanager sql
dbmanager query rows
dbmanager result rows
dbmanager row count
dbmanager affected rows
dbmanager error
```

Query rows are returned as JSON strings, one string per row.
