package com.drewdrew1;

import com.drewdrew1.api.Database;
import com.drewdrew1.api.DatabaseService;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private LevelDatabase levelDatabase;

    @Override
    public void onEnable() {
        try {
            Database database = loadDatabase();
            levelDatabase = new LevelDatabase(database);
            levelDatabase.migrate().join();
            getLogger().info("Connected to DBManager database: " + database.path());
        } catch (IllegalStateException | CompletionException exception) {
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            getLogger().log(Level.SEVERE, "DBManager database initialization failed.", cause);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        levelDatabase = null;
    }

    public LevelDatabase levelDatabase() {
        if (levelDatabase == null) {
            throw new IllegalStateException("Level database is not initialized.");
        }
        return levelDatabase;
    }

    private Database loadDatabase() {
        RegisteredServiceProvider<DatabaseService> provider =
                Bukkit.getServicesManager().getRegistration(DatabaseService.class);

        if (provider == null) {
            throw new IllegalStateException("DBManager is not loaded.");
        }

        return provider.getProvider().database("rpg");
    }
}
