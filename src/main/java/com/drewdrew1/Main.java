package com.drewdrew1;

import com.drewdrew1.api.Database;
import com.drewdrew1.api.DatabaseService;
import com.drewdrew1.config.SkillConfigCache;
import com.drewdrew1.integration.placeholder.PlaceholderApiIntegration;
import com.drewdrew1.integration.skript.SkriptIntegration;
import com.drewdrew1.listener.LevelEventListener;
import com.drewdrew1.progress.LevelProgressService;
import com.drewdrew1.command.LevelSystemCommand;
import com.drewdrew1.progress.PlacedBlockTracker;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private LevelDatabase levelDatabase;
    private SkillConfigCache skillConfigCache;
    private LevelProgressService levelProgressService;
    private PlacedBlockTracker placedBlockTracker;
    private Runnable placeholderApiUnregister = () -> {
    };

    @Override
    public void onEnable() {
        try {
            skillConfigCache = new SkillConfigCache(this);
            skillConfigCache.copyDefaultsIfMissing();
            skillConfigCache.reload();
            getLogger().info("Loaded " + skillConfigCache.skillConfigs().size()
                    + " level JSON configs from " + skillConfigCache.jsonDirectory());

            Database database = loadDatabase();
            levelDatabase = new LevelDatabase(database);
            levelDatabase.migrate().join();
            getLogger().info("Connected to DBManager database: " + database.path());

            placedBlockTracker = new PlacedBlockTracker(this, levelDatabase);
            placedBlockTracker.load().join();
            levelProgressService = new LevelProgressService(this, levelDatabase, skillConfigCache);
            getServer().getPluginManager().registerEvents(
                    new LevelEventListener(this, levelProgressService, placedBlockTracker),
                    this
            );
            LevelSystemCommand command = new LevelSystemCommand(this);
            PluginCommand pluginCommand = Objects.requireNonNull(getCommand("levelSystem"), "levelSystem command");
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
            registerOptionalIntegrations();
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Level JSON initialization failed.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
        } catch (JsonParseException exception) {
            getLogger().log(Level.SEVERE, "Level JSON parsing failed.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
        } catch (IllegalStateException | CompletionException exception) {
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            getLogger().log(Level.SEVERE, "Plugin initialization failed.", cause);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (levelProgressService != null) {
            try {
                levelProgressService.shutdown().join();
            } catch (CompletionException exception) {
                getLogger().log(Level.WARNING, "Failed to flush level progress before shutdown.", exception);
            }
        }
        if (placedBlockTracker != null) {
            try {
                placedBlockTracker.flush().join();
            } catch (CompletionException exception) {
                getLogger().log(Level.WARNING, "Failed to flush placed block state before shutdown.", exception);
            }
        }
        try {
            placeholderApiUnregister.run();
        } catch (RuntimeException | LinkageError exception) {
            getLogger().log(Level.WARNING, "Failed to unregister PlaceholderAPI expansion.", exception);
        }
        placeholderApiUnregister = () -> {
        };
        placedBlockTracker = null;
        levelProgressService = null;
        skillConfigCache = null;
        levelDatabase = null;
    }

    public void reloadSkillConfigs() throws IOException {
        if (levelProgressService != null) {
            levelProgressService.flush().join();
        }
        skillConfigCache().copyDefaultsIfMissing();
        skillConfigCache().reload();
        if (levelProgressService != null) {
            levelProgressService.clearMemoryCache();
        }
    }

    public LevelProgressService levelProgressService() {
        if (levelProgressService == null) {
            throw new IllegalStateException("Level progress service is not initialized.");
        }
        return levelProgressService;
    }

    public SkillConfigCache skillConfigCache() {
        if (skillConfigCache == null) {
            throw new IllegalStateException("Skill config cache is not initialized.");
        }
        return skillConfigCache;
    }

    public LevelDatabase levelDatabase() {
        if (levelDatabase == null) {
            throw new IllegalStateException("Level database is not initialized.");
        }
        return levelDatabase;
    }

    private void registerOptionalIntegrations() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                placeholderApiUnregister = PlaceholderApiIntegration.register(this);
            } catch (RuntimeException | LinkageError exception) {
                getLogger().log(Level.WARNING, "PlaceholderAPI integration failed to register.", exception);
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Skript")) {
            try {
                SkriptIntegration.register(this);
            } catch (RuntimeException | LinkageError exception) {
                getLogger().log(Level.WARNING, "Skript integration failed to register.", exception);
            }
        }
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
