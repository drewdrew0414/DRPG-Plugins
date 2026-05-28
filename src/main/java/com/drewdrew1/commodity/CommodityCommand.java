package com.drewdrew1.commodity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class CommodityCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ACTIONS = List.of("조회", "지급", "차감", "설정", "리셋", "초기화", "reload", "리로드");

    private final CommodityService service;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.KOREA);

    public CommodityCommand(CommodityService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("commodity.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다. commodity.admin 권한이 필요합니다.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String action = normalizeAction(args[0]);
        if ("reload".equals(action)) {
            handleReload(sender);
            return true;
        }

        if (args.length < 2) {
            sendHelp(sender, label);
            return true;
        }

        Target target = resolveTarget(args[1]);
        switch (action) {
            case "get" -> handleGet(sender, target, args);
            case "give" -> handleChange(sender, target, args, CommodityOperation.ADD, "지급");
            case "take" -> handleChange(sender, target, args, CommodityOperation.SUBTRACT, "차감");
            case "set" -> handleChange(sender, target, args, CommodityOperation.SET, "설정");
            case "reset" -> handleReset(sender, target, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("commodity.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return startsWith(ACTIONS, args[0]);
        }
        if (args.length == 2 && !"reload".equals(normalizeAction(args[0]))) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return startsWith(names, args[1]);
        }
        if (args.length == 3) {
            String action = normalizeAction(args[0]);
            if (List.of("get", "give", "take", "set", "reset").contains(action)) {
                List<String> commodities = new ArrayList<>();
                commodities.add("all");
                for (CommodityDefinition definition : service.settings().definitions()) {
                    commodities.add(definition.key());
                    commodities.add(definition.displayName());
                }
                return startsWith(commodities, args[2]);
            }
        }
        if (args.length == 4 && List.of("give", "take", "set").contains(normalizeAction(args[0]))) {
            return startsWith(List.of("1", "10", "100", "1000"), args[3]);
        }
        return Collections.emptyList();
    }

    private void handleReload(CommandSender sender) {
        reply(service.reload(), sender, settings -> sender.sendMessage(
                ChatColor.GREEN + "재화 설정을 다시 불러왔습니다. config: " + service.config().configPath()
        ));
    }

    private void handleGet(CommandSender sender, Target target, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /재화 조회 <플레이어> <재화>");
            return;
        }
        CommodityDefinition definition = findCommodity(sender, args[2]);
        if (definition == null) {
            return;
        }

        reply(service.snapshot(target.player(), target.name()), sender, data -> {
            long amount = data.amount(definition.key(), definition.defaultValue());
            sender.sendMessage(ChatColor.AQUA + data.playerName() + ChatColor.GRAY + "님의 "
                    + ChatColor.WHITE + definition.displayName() + ChatColor.GRAY + ": "
                    + ChatColor.GREEN + format(amount) + ChatColor.GRAY + " / " + format(definition.max()));
        });
    }

    private void handleChange(
            CommandSender sender,
            Target target,
            String[] args,
            CommodityOperation operation,
            String operationName
    ) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /재화 " + operationName + " <플레이어> <재화> <값>");
            return;
        }
        CommodityDefinition definition = findCommodity(sender, args[2]);
        if (definition == null) {
            return;
        }
        Long amount = parseAmount(sender, args[3]);
        if (amount == null) {
            return;
        }
        if (operation != CommodityOperation.SET && amount < 0L) {
            sender.sendMessage(ChatColor.RED + "지급/차감 값은 0 이상이어야 합니다.");
            return;
        }

        reply(service.change(target.player(), target.name(), definition, operation, amount), sender, result -> {
            sender.sendMessage(ChatColor.GREEN + result.playerName() + ChatColor.GRAY + "님의 "
                    + ChatColor.WHITE + definition.displayName() + ChatColor.GRAY + " "
                    + operationName + " 완료: "
                    + ChatColor.YELLOW + format(result.oldAmount())
                    + ChatColor.GRAY + " -> "
                    + ChatColor.GREEN + format(result.newAmount()));
        });
    }

    private void handleReset(CommandSender sender, Target target, String[] args) {
        if (args.length < 3 || "all".equalsIgnoreCase(args[2])) {
            reply(service.resetAll(target.player(), target.name()), sender, data -> sender.sendMessage(
                    ChatColor.GREEN + data.playerName() + ChatColor.GRAY + "님의 모든 재화를 기본값으로 리셋했습니다."
            ));
            return;
        }

        CommodityDefinition definition = findCommodity(sender, args[2]);
        if (definition == null) {
            return;
        }
        reply(service.change(target.player(), target.name(), definition, CommodityOperation.RESET, 0L), sender, result -> {
            sender.sendMessage(ChatColor.GREEN + result.playerName() + ChatColor.GRAY + "님의 "
                    + ChatColor.WHITE + definition.displayName() + ChatColor.GRAY + " 리셋 완료: "
                    + ChatColor.YELLOW + format(result.oldAmount())
                    + ChatColor.GRAY + " -> "
                    + ChatColor.GREEN + format(result.newAmount()));
        });
    }

    private CommodityDefinition findCommodity(CommandSender sender, String input) {
        return service.settings().find(input).orElseGet(() -> {
            sender.sendMessage(ChatColor.RED + "알 수 없는 재화입니다: " + input);
            return null;
        });
    }

    private Long parseAmount(CommandSender sender, String raw) {
        try {
            return Long.parseLong(raw.replace(",", ""));
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "값은 정수로 입력해야 합니다: " + raw);
            return null;
        }
    }

    private <T> void reply(CompletableFuture<T> future, CommandSender sender, Consumer<T> success) {
        future.whenComplete((result, error) -> service.runSync(() -> {
            if (error != null) {
                sender.sendMessage(ChatColor.RED + "재화 처리 중 오류가 발생했습니다: "
                        + CommodityService.unwrap(error).getMessage());
                return;
            }
            success.accept(result);
        }));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "재화 관리 명령어");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " 조회 <플레이어> <재화>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " 지급 <플레이어> <재화> <값>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " 차감 <플레이어> <재화> <값>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " 설정 <플레이어> <재화> <값>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " 리셋 <플레이어> [재화|all]");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " 리로드");
    }

    private Target resolveTarget(String raw) {
        try {
            UUID uuid = UUID.fromString(raw);
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return new Target(player, raw);
        } catch (IllegalArgumentException ignored) {
            Player online = Bukkit.getPlayerExact(raw);
            if (online != null) {
                return new Target(online, online.getName());
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(raw);
            return new Target(player, raw);
        }
    }

    private String normalizeAction(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "조회", "get", "check", "확인" -> "get";
            case "지급", "give", "add", "추가" -> "give";
            case "차감", "take", "remove", "subtract", "감소" -> "take";
            case "설정", "set" -> "set";
            case "리셋", "초기화", "reset" -> "reset";
            case "리로드", "reload" -> "reload";
            default -> action.toLowerCase(Locale.ROOT);
        };
    }

    private String format(long value) {
        return numberFormat.format(value);
    }

    private static List<String> startsWith(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .distinct()
                .limit(30)
                .toList();
    }

    private record Target(OfflinePlayer player, String name) {
    }
}
