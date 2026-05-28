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
