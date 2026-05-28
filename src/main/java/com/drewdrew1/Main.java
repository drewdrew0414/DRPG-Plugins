package com.drewdrew1;

import com.drewdrew1.command.GuildCommand;
import com.drewdrew1.command.StorageCommand;
import com.drewdrew1.config.StorageGuildConfig;
import com.drewdrew1.db.StorageRepository;
import com.drewdrew1.guild.GuildService;
import com.drewdrew1.listener.ChatPromptListener;
import com.drewdrew1.listener.StorageListener;
import com.drewdrew1.listener.TicketListener;
import com.drewdrew1.storage.StorageManager;
import com.drewdrew1.ticket.TicketService;
import com.drewdrew1.api.Database;
import com.drewdrew1.database.SQLiteDatabaseService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private SQLiteDatabaseService databaseService;
    private StorageRepository repository;
    private StorageManager storageManager;
    private GuildService guildService;
    private TicketService ticketService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        StorageGuildConfig config = StorageGuildConfig.load(getConfig());
        databaseService = new SQLiteDatabaseService(this);
        Database database = databaseService.database("storageGuild");
        repository = new StorageRepository(database, config);
        repository.initialize();

        ticketService = new TicketService(this, config);
        guildService = new GuildService(this, repository, ticketService, config);
        storageManager = new StorageManager(this, repository, guildService, config);

        getServer().getPluginManager().registerEvents(new StorageListener(storageManager), this);
        getServer().getPluginManager().registerEvents(new TicketListener(storageManager, guildService, ticketService), this);
        getServer().getPluginManager().registerEvents(new ChatPromptListener(this, guildService), this);

        StorageCommand storageCommand = new StorageCommand(this, storageManager, guildService, ticketService, config);
        Objects.requireNonNull(getCommand("storage")).setExecutor(storageCommand);
        Objects.requireNonNull(getCommand("storage")).setTabCompleter(storageCommand);

        GuildCommand guildCommand = new GuildCommand(this, guildService, storageManager, ticketService);
        Objects.requireNonNull(getCommand("guild")).setExecutor(guildCommand);
        Objects.requireNonNull(getCommand("guild")).setTabCompleter(guildCommand);

        getLogger().info("storageGuild enabled.");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.flushAll();
        }
        if (databaseService != null) {
            databaseService.close();
            databaseService = null;
        }
    }
}
