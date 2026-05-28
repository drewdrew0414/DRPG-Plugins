package com.drewdrew1;

import com.drewdrew1.api.DatabaseService;
import com.drewdrew1.database.SQLiteDatabaseService;
import com.drewdrew1.skript.SkriptIntegration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private SQLiteDatabaseService databaseService;

    @Override
    public void onEnable() {
        databaseService = new SQLiteDatabaseService(this);
        getServer().getServicesManager().register(
                DatabaseService.class,
                databaseService,
                this,
                ServicePriority.Normal
        );

        getLogger().info("SQLite API service is ready.");

        if (getServer().getPluginManager().getPlugin("Skript") != null) {
            try {
                SkriptIntegration.register(this, databaseService);
            } catch (LinkageError | RuntimeException exception) {
                getLogger().warning("Skript integration could not be enabled: " + exception.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (databaseService == null) {
            return;
        }

        getServer().getServicesManager().unregister(DatabaseService.class, databaseService);
        databaseService.close();
        databaseService = null;
    }
}
