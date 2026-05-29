package com.drewdrew1.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.drewdrew1.model.Guild;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public final class EffStorageGuild extends Effect {
    private int pattern;
    private Expression<Player> player;
    private Expression<Player> target;
    private Expression<String> text;
    private Expression<Number> number;

    static void register() {
        Skript.registerEffect(EffStorageGuild.class,
                "open [storageguild] personal storage of %player%",
                "open [storageguild] personal storage of %player% at page %number%",
                "open [storageguild] guild storage %string% for %player%",
                "open [storageguild] guild storage %string% for %player% at page %number%",
                "open [storageguild] guild selector for %player%",
                "create [storageguild] guild %string% for %player%",
                "send [storageguild] guild invite to %player% for guild %string% by %player%",
                "force send [storageguild] guild invite to %player% for guild %string% by %player%",
                "make %player% accept [storageguild] guild invite %string%",
                "make %player% deny [storageguild] guild invite %string%",
                "expand [storageguild] personal storage of %player%",
                "expand [storageguild] guild storage %string% for %player%",
                "make %player% leave [storageguild] guild %string%",
                "disband [storageguild] guild %string% by %player%",
                "kick %player% from [storageguild] guild %string% by %player%",
                "promote %player% in [storageguild] guild %string% by %player%",
                "demote %player% in [storageguild] guild %string% by %player%",
                "give %number% [storageguild] guild invite ticket[s] to %player%",
                "give %number% [storageguild] storage expansion ticket[s] to %player%"
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.pattern = matchedPattern;
        switch (matchedPattern) {
            case 0, 4, 10 -> player = (Expression<Player>) expressions[0];
            case 1 -> {
                player = (Expression<Player>) expressions[0];
                number = (Expression<Number>) expressions[1];
            }
            case 2, 11, 13 -> {
                text = (Expression<String>) expressions[0];
                player = (Expression<Player>) expressions[1];
            }
            case 3 -> {
                text = (Expression<String>) expressions[0];
                player = (Expression<Player>) expressions[1];
                number = (Expression<Number>) expressions[2];
            }
            case 5 -> {
                text = (Expression<String>) expressions[0];
                player = (Expression<Player>) expressions[1];
            }
            case 6, 7, 14, 15, 16 -> {
                target = (Expression<Player>) expressions[0];
                text = (Expression<String>) expressions[1];
                player = (Expression<Player>) expressions[2];
            }
            case 8, 9, 12 -> {
                player = (Expression<Player>) expressions[0];
                text = (Expression<String>) expressions[1];
            }
            case 17, 18 -> {
                number = (Expression<Number>) expressions[0];
                player = (Expression<Player>) expressions[1];
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        switch (pattern) {
            case 0 -> openPersonal(event, 0);
            case 1 -> openPersonal(event, page(event));
            case 2 -> openGuild(event, 0);
            case 3 -> openGuild(event, page(event));
            case 4 -> openGuildSelector(event);
            case 5 -> createGuild(event);
            case 6 -> invite(event, false);
            case 7 -> invite(event, true);
            case 8 -> acceptInvite(event);
            case 9 -> denyInvite(event);
            case 10 -> expandPersonal(event);
            case 11 -> expandGuild(event);
            case 12 -> leaveGuild(event);
            case 13 -> disband(event);
            case 14 -> kick(event);
            case 15 -> promote(event);
            case 16 -> demote(event);
            case 17 -> giveInviteTickets(event);
            case 18 -> giveExpansionTickets(event);
            default -> {
            }
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "storageGuild effect " + pattern;
    }

    private void openPersonal(Event event, int page) {
        Player value = player(event);
        if (value != null) {
            SkriptIntegration.storageManager().openPersonal(value, page);
        }
    }

    private void openGuild(Event event, int page) {
        Player value = player(event);
        String guildName = text(event);
        if (value == null || guildName == null) {
            return;
        }
        SkriptIntegration.guildService().guild(guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable == null && guild != null && guild.isPresent()) {
                SkriptIntegration.storageManager().openGuild(value, guild.get(), page);
            }
        }));
    }

    private void openGuildSelector(Event event) {
        Player value = player(event);
        if (value == null) {
            return;
        }
        SkriptIntegration.guildService().guildsFor(value.getUniqueId()).whenComplete((guilds, throwable) -> runSync(() -> {
            if (throwable == null) {
                SkriptIntegration.storageManager().openGuildSelector(value, guilds);
            }
        }));
    }

    private void createGuild(Event event) {
        Player value = player(event);
        String guildName = text(event);
        if (value != null && guildName != null) {
            SkriptIntegration.guildService().createGuild(value, guildName);
        }
    }

    private void invite(Event event, boolean force) {
        Player inviter = player(event);
        Player targetPlayer = target(event);
        String guildName = text(event);
        if (inviter == null || targetPlayer == null || guildName == null) {
            return;
        }
        if (force) {
            SkriptIntegration.guildService().forceInvite(inviter, targetPlayer.getName(), guildName);
        } else {
            SkriptIntegration.guildService().inviteByCommand(inviter, targetPlayer.getName(), guildName);
        }
    }

    private void acceptInvite(Event event) {
        Player value = player(event);
        String guildName = text(event);
        if (value != null && guildName != null) {
            SkriptIntegration.guildService().acceptInvite(value, guildName);
        }
    }

    private void denyInvite(Event event) {
        Player value = player(event);
        String guildName = text(event);
        if (value != null && guildName != null) {
            SkriptIntegration.guildService().denyInvite(value, guildName);
        }
    }

    private void expandPersonal(Event event) {
        Player value = player(event);
        if (value != null) {
            SkriptIntegration.storageManager().expandPersonal(value);
        }
    }

    private void expandGuild(Event event) {
        Player value = player(event);
        String guildName = text(event);
        if (value == null || guildName == null) {
            return;
        }
        SkriptIntegration.guildService().guild(guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable == null && guild != null && guild.isPresent()) {
                SkriptIntegration.storageManager().expandGuild(value, guild.get());
            }
        }));
    }

    private void leaveGuild(Event event) {
        Player value = player(event);
        String guildName = text(event);
        if (value != null && guildName != null) {
            SkriptIntegration.guildService().leaveGuild(value, guildName);
        }
    }

    private void disband(Event event) {
        Player value = player(event);
        String guildName = text(event);
        if (value != null && guildName != null) {
            SkriptIntegration.guildService().disband(value, guildName);
        }
    }

    private void kick(Event event) {
        Player actor = player(event);
        Player targetPlayer = target(event);
        String guildName = text(event);
        if (actor != null && targetPlayer != null && guildName != null) {
            SkriptIntegration.guildService().kick(actor, targetPlayer.getName(), guildName);
        }
    }

    private void promote(Event event) {
        Player actor = player(event);
        Player targetPlayer = target(event);
        String guildName = text(event);
        if (actor != null && targetPlayer != null && guildName != null) {
            SkriptIntegration.guildService().promote(actor, targetPlayer.getName(), guildName);
        }
    }

    private void demote(Event event) {
        Player actor = player(event);
        Player targetPlayer = target(event);
        String guildName = text(event);
        if (actor != null && targetPlayer != null && guildName != null) {
            SkriptIntegration.guildService().demote(actor, targetPlayer.getName(), guildName);
        }
    }

    private void giveInviteTickets(Event event) {
        Player value = player(event);
        if (value != null) {
            value.getInventory().addItem(SkriptIntegration.ticketService().createInviteTicket(amount(event)));
        }
    }

    private void giveExpansionTickets(Event event) {
        Player value = player(event);
        if (value != null) {
            value.getInventory().addItem(SkriptIntegration.ticketService().createExpansionTicket(amount(event)));
        }
    }

    private Player player(Event event) {
        return player == null ? null : player.getSingle(event);
    }

    private Player target(Event event) {
        return target == null ? null : target.getSingle(event);
    }

    private String text(Event event) {
        String value = text == null ? null : text.getSingle(event);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int page(Event event) {
        Number value = number == null ? null : number.getSingle(event);
        return value == null ? 0 : Math.max(0, value.intValue() - 1);
    }

    private int amount(Event event) {
        Number value = number == null ? null : number.getSingle(event);
        return value == null ? 1 : Math.max(1, Math.min(64, value.intValue()));
    }

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(SkriptIntegration.plugin(), runnable);
    }
}
