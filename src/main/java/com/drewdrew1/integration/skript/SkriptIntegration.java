package com.drewdrew1.integration.skript;

import ch.njol.skript.Skript;
import com.drewdrew1.Main;
import com.drewdrew1.guild.GuildService;
import com.drewdrew1.integration.IntegrationQueries;
import com.drewdrew1.storage.StorageManager;
import com.drewdrew1.ticket.TicketService;

public final class SkriptIntegration {
    private static Main plugin;
    private static IntegrationQueries queries;
    private static GuildService guildService;
    private static StorageManager storageManager;
    private static TicketService ticketService;
    private static boolean registered;

    private SkriptIntegration() {
    }

    public static void register(
            Main plugin,
            IntegrationQueries queries,
            GuildService guildService,
            StorageManager storageManager,
            TicketService ticketService
    ) {
        SkriptIntegration.plugin = plugin;
        SkriptIntegration.queries = queries;
        SkriptIntegration.guildService = guildService;
        SkriptIntegration.storageManager = storageManager;
        SkriptIntegration.ticketService = ticketService;

        if (registered) {
            return;
        }
        Skript.registerAddon(plugin);
        EffStorageGuild.register();
        CondStorageGuild.register();
        ExprStorageGuildText.register();
        ExprStorageGuildNumber.register();
        registered = true;
    }

    public static Main plugin() {
        return plugin;
    }

    public static IntegrationQueries queries() {
        return queries;
    }

    public static GuildService guildService() {
        return guildService;
    }

    public static StorageManager storageManager() {
        return storageManager;
    }

    public static TicketService ticketService() {
        return ticketService;
    }
}
