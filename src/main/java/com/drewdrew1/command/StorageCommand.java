package com.drewdrew1.command;

import com.drewdrew1.Main;
import com.drewdrew1.config.StorageGuildConfig;
import com.drewdrew1.guild.GuildService;
import com.drewdrew1.model.Guild;
import com.drewdrew1.storage.StorageManager;
import com.drewdrew1.ticket.TicketService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class StorageCommand implements TabExecutor {
    private final Main plugin;
    private final StorageManager storageManager;
    private final GuildService guildService;
    private final TicketService ticketService;
    private final StorageGuildConfig config;

    public StorageCommand(Main plugin, StorageManager storageManager, GuildService guildService, TicketService ticketService, StorageGuildConfig config) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.guildService = guildService;
        this.ticketService = ticketService;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            storageManager.openPersonal(player, 0);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (isPositiveInteger(sub)) {
            storageManager.openPersonal(player, parsePage(sub));
            return true;
        }

        switch (sub) {
            case "personal", "개인" -> storageManager.openPersonal(player, args.length >= 2 ? parsePage(args[1]) : 0);
            case "guild", "길드" -> handleGuildStorage(player, args);
            case "expand", "확장" -> handleExpand(player, args);
            case "ticket", "티켓" -> handleTicket(player, args);
            default -> sendUsage(player, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("personal", "guild", "expand", "ticket"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ticket")) {
            return filter(List.of("expansion"), args[1]);
        }
        return List.of();
    }

    private void handleGuildStorage(Player player, String[] args) {
        if (args.length == 1) {
            guildService.guildsFor(player.getUniqueId()).whenComplete((guilds, throwable) -> runSync(() -> {
                if (throwable != null) {
                    player.sendMessage("길드 목록을 불러오지 못했습니다: " + rootMessage(throwable));
                    return;
                }
                if (guilds.isEmpty()) {
                    player.sendMessage("가입한 길드가 없습니다.");
                    return;
                }
                if (guilds.size() == 1) {
                    storageManager.openGuild(player, guilds.getFirst(), 0);
                    return;
                }
                storageManager.openGuildSelector(player, guilds);
            }));
            return;
        }

        String guildName = args[1];
        int page = args.length >= 3 ? parsePage(args[2]) : 0;
        guildService.guild(guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null || guild.isEmpty()) {
                player.sendMessage("길드를 찾지 못했습니다.");
                return;
            }
            storageManager.openGuild(player, guild.get(), page);
        }));
    }

    private void handleExpand(Player player, String[] args) {
        if (!ticketService.isExpansionTicket(player.getInventory().getItemInMainHand())
                && !ticketService.isExpansionTicket(player.getInventory().getItemInOffHand())) {
            player.sendMessage("창고 확장권을 손에 들고 있어야 합니다.");
            return;
        }
        if (args.length == 1) {
            storageManager.expandPersonal(player, () -> ticketService.consumeExpansionTicket(player));
            return;
        }
        String guildName = args[1];
        guildService.guild(guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null || guild.isEmpty()) {
                player.sendMessage("길드를 찾지 못했습니다.");
                return;
            }
            storageManager.expandGuild(player, guild.get(), () -> ticketService.consumeExpansionTicket(player));
        }));
    }

    private void handleTicket(Player player, String[] args) {
        if (!player.hasPermission("storageguild.admin")) {
            player.sendMessage("권한이 없습니다.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("expansion")) {
            player.sendMessage("/storage ticket expansion [수량]");
            return;
        }
        int amount = args.length >= 3 ? parseAmount(args[2]) : 1;
        player.getInventory().addItem(ticketService.createExpansionTicket(amount));
        player.sendMessage("창고 확장권 " + amount + "장을 지급했습니다.");
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage("/" + label + " [personal|guild|expand|ticket]");
        player.sendMessage("/" + label + " guild [길드] [페이지]");
        player.sendMessage("/" + label + " expand [길드]");
    }

    private int parsePage(String raw) {
        if (!isPositiveInteger(raw)) {
            return 0;
        }
        return Math.max(0, Integer.parseInt(raw) - 1);
    }

    private int parseAmount(String raw) {
        if (!isPositiveInteger(raw)) {
            return 1;
        }
        return Math.max(1, Math.min(64, Integer.parseInt(raw)));
    }

    private boolean isPositiveInteger(String raw) {
        return raw != null && raw.matches("[1-9][0-9]*");
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                result.add(value);
            }
        }
        return result;
    }

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
