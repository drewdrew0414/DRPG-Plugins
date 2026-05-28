package com.drewdrew1.listener;

import com.drewdrew1.Main;
import com.drewdrew1.guild.GuildService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ChatPromptListener implements Listener {
    private final Main plugin;
    private final GuildService guildService;

    public ChatPromptListener(Main plugin, GuildService guildService) {
        this.plugin = plugin;
        this.guildService = guildService;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!guildService.hasPrompt(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> guildService.handlePrompt(player, message));
    }
}
