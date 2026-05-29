package com.drewdrew1.integration;

import com.drewdrew1.Main;
import com.drewdrew1.config.StorageGuildConfig;
import com.drewdrew1.db.StorageRepository;
import com.drewdrew1.guild.GuildService;
import com.drewdrew1.integration.papi.StorageGuildExpansion;
import com.drewdrew1.integration.skript.SkriptIntegration;
import com.drewdrew1.storage.StorageManager;
import com.drewdrew1.ticket.TicketService;
import org.bukkit.Bukkit;

public final class IntegrationManager {
    private final Main plugin;
    private final StorageRepository repository;
    private final GuildService guildService;
    private final StorageManager storageManager;
    private final TicketService ticketService;
    private final StorageGuildConfig config;
    private StorageGuildExpansion expansion;

    public IntegrationManager(
            Main plugin,
            StorageRepository repository,
            GuildService guildService,
            StorageManager storageManager,
            TicketService ticketService,
            StorageGuildConfig config
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.guildService = guildService;
        this.storageManager = storageManager;
        this.ticketService = ticketService;
        this.config = config;
    }

    public void register() {
        IntegrationQueries queries = new IntegrationQueries(repository, config);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new StorageGuildExpansion(plugin, queries);
            expansion.register();
            plugin.getLogger().info("PlaceholderAPI expansion registered.");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Skript")) {
            SkriptIntegration.register(plugin, queries, guildService, storageManager, ticketService);
            plugin.getLogger().info("Skript syntax registered.");
        }
    }

    public void unregister() {
        if (expansion != null && expansion.isRegistered()) {
            expansion.unregister();
            expansion = null;
        }
    }
}
