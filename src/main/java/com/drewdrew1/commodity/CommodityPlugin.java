package com.drewdrew1.commodity;

import com.drewdrew1.api.Database;
import com.drewdrew1.api.DatabaseService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Level;

public final class CommodityPlugin extends JavaPlugin implements Listener {
    private CommodityService commodityService;
    private CommodityPlaceholderExpansion placeholderExpansion;
    private boolean skriptHookRegistered;

    @Override
    public void onEnable() {
        CommodityConfig config = new CommodityConfig(configPath());
        CommoditySettings settings;
        try {
            settings = config.load();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to load commodity config.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        RegisteredServiceProvider<DatabaseService> provider = getServer()
                .getServicesManager()
                .getRegistration(DatabaseService.class);
        if (provider == null) {
            getLogger().severe("DBManager service is not available. Install and enable DBManager-1.0.0.jar.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Database database = provider.getProvider().database(settings.databaseName());
        CommodityRepository repository = new CommodityRepository(database);
        try {
            repository.initialize().join();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize commodity database.", CommodityService.unwrap(exception));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        commodityService = new CommodityService(this, config, repository, settings);
        CommodityCommand command = new CommodityCommand(commodityService);
        if (getCommand("commodity") != null) {
            getCommand("commodity").setExecutor(command);
            getCommand("commodity").setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(this, this);
        tryRegisterPlaceholderExpansion();
        tryRegisterSkriptHook();
        commodityService.loadOnlinePlayers(true)
                .exceptionally(error -> {
                    getLogger().log(Level.WARNING, "Failed to load online commodity data.", CommodityService.unwrap(error));
                    return null;
                });
        getLogger().info("RPG commodity system is enabled. Config: " + config.configPath());
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null && placeholderExpansion.isRegistered()) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (commodityService != null) {
            commodityService.shutdown();
            commodityService = null;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (commodityService == null) {
            return;
        }
        commodityService.loadPlayer(event.getPlayer(), event.getPlayer().getName(), true)
                .exceptionally(error -> {
                    getLogger().log(Level.WARNING, "Failed to load commodity data for " + event.getPlayer().getName(), CommodityService.unwrap(error));
                    return null;
                });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (commodityService != null) {
            commodityService.markOffline(event.getPlayer());
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        Plugin plugin = event.getPlugin();
        if ("PlaceholderAPI".equals(plugin.getName())) {
            tryRegisterPlaceholderExpansion();
        }
        if ("Skript".equals(plugin.getName())) {
            tryRegisterSkriptHook();
        }
    }

    private void tryRegisterPlaceholderExpansion() {
        if (commodityService == null || placeholderExpansion != null) {
            return;
        }
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            placeholderExpansion = new CommodityPlaceholderExpansion(this, commodityService);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI expansion 'commodity' is registered.");
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(Level.WARNING, "PlaceholderAPI integration failed.", exception);
            placeholderExpansion = null;
        }
    }

    private void tryRegisterSkriptHook() {
        if (commodityService == null || skriptHookRegistered) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("Skript") == null) {
            return;
        }
        try {
            CommoditySkriptHook.register(this, commodityService);
            skriptHookRegistered = true;
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(Level.WARNING, "Skript integration failed.", exception);
        }
    }

    private Path configPath() {
        Path pluginsPath = getDataFolder().toPath().getParent();
        if (pluginsPath == null) {
            pluginsPath = Path.of("plugins");
        }
        return pluginsPath.resolve("RPG").resolve("commodity").resolve("config.json");
    }
}
