package com.drewdrew1.command;

import com.drewdrew1.Main;
import com.drewdrew1.guild.GuildService;
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

public final class GuildCommand implements TabExecutor {
    private final Main plugin;
    private final GuildService guildService;
    private final StorageManager storageManager;
    private final TicketService ticketService;

    public GuildCommand(Main plugin, GuildService guildService, StorageManager storageManager, TicketService ticketService) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.storageManager = storageManager;
        this.ticketService = ticketService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create", "생성" -> {
                if (args.length < 2) {
                    player.sendMessage("/" + label + " create <이름>");
                    return true;
                }
                guildService.createGuild(player, args[1]);
            }
            case "invite", "초대" -> {
                if (args.length < 2) {
                    player.sendMessage("/" + label + " invite <플레이어> [길드]");
                    return true;
                }
                guildService.inviteByCommand(player, args[1], args.length >= 3 ? args[2] : null);
            }
            case "accept", "수락" -> {
                if (args.length < 2) {
                    player.sendMessage("/" + label + " accept <길드>");
                    return true;
                }
                guildService.acceptInvite(player, args[1]);
            }
            case "deny", "거절" -> {
                if (args.length < 2) {
                    player.sendMessage("/" + label + " deny <길드>");
                    return true;
                }
                guildService.denyInvite(player, args[1]);
            }
            case "invites", "초대목록" -> guildService.showInvites(player);
            case "leave", "탈퇴" -> guildService.leaveGuild(player, args.length >= 2 ? args[1] : null);
            case "kick", "추방" -> {
                if (args.length < 2) {
                    player.sendMessage("/" + label + " kick <플레이어> [길드]");
                    return true;
                }
                guildService.kick(player, args[1], args.length >= 3 ? args[2] : null);
            }
            case "disband", "해체" -> guildService.disband(player, args.length >= 2 ? args[1] : null);
            case "promote", "승급" -> {
                if (args.length < 2) {
                    player.sendMessage("/" + label + " promote <플레이어> [길드]");
                    return true;
                }
                guildService.promote(player, args[1], args.length >= 3 ? args[2] : null);
            }
            case "demote", "강등" -> {
                if (args.length < 2) {
                    player.sendMessage("/" + label + " demote <플레이어> [길드]");
                    return true;
                }
                guildService.demote(player, args[1], args.length >= 3 ? args[2] : null);
            }
            case "members", "멤버" -> guildService.showMembers(player, args.length >= 2 ? args[1] : null);
            case "list", "목록" -> guildService.listGuilds(player);
            case "storage", "창고" -> handleStorage(player, args);
            case "ticket", "티켓" -> handleTicket(player, args);
            default -> sendUsage(player, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "invite", "accept", "deny", "invites", "leave", "kick", "disband", "promote", "demote", "members", "list", "storage", "ticket"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ticket")) {
            return filter(List.of("invite"), args[1]);
        }
        return List.of();
    }

    private void handleStorage(Player player, String[] args) {
        guildService.resolveJoinedGuild(player, args.length >= 2 ? args[1] : null)
                .whenComplete((guild, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        player.sendMessage(rootMessage(throwable));
                        return;
                    }
                    storageManager.openGuild(player, guild, 0);
                }));
    }

    private void handleTicket(Player player, String[] args) {
        if (!player.hasPermission("storageguild.admin")) {
            player.sendMessage("권한이 없습니다.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("invite")) {
            player.sendMessage("/guild ticket invite [수량]");
            return;
        }
        int amount = args.length >= 3 && args[2].matches("[1-9][0-9]*") ? Math.min(64, Integer.parseInt(args[2])) : 1;
        player.getInventory().addItem(ticketService.createInviteTicket(amount));
        player.sendMessage("길드 초대권 " + amount + "장을 지급했습니다.");
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage("/" + label + " create <이름>");
        player.sendMessage("/" + label + " invite <플레이어> [길드]");
        player.sendMessage("/" + label + " accept <길드>");
        player.sendMessage("/" + label + " list | members [길드] | storage [길드]");
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
