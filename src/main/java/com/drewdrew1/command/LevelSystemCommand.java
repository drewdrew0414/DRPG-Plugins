package com.drewdrew1.command;

import com.drewdrew1.Main;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

public final class LevelSystemCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;

    public LevelSystemCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("levelsystem.reload")) {
                sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
                return true;
            }

            try {
                plugin.reloadSkillConfigs();
                sender.sendMessage(ChatColor.GREEN + "levelSystem JSON 캐시를 다시 불러왔습니다. 로드된 스킬: "
                        + plugin.skillConfigCache().skillConfigs().size());
            } catch (IOException | JsonParseException | CompletionException exception) {
                sender.sendMessage(ChatColor.RED + "levelSystem JSON reload 실패: " + exception.getMessage());
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to reload level JSON configs.", exception);
            }
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length != 1 || !sender.hasPermission("levelsystem.reload")) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return "reload".startsWith(prefix) ? List.of("reload") : List.of();
    }
}
