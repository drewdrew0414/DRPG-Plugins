package com.drewdrew1.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

public final class CondStorageGuild extends Condition {
    private int pattern;
    private Expression<Player> player;
    private Expression<String> guildName;

    static void register() {
        Skript.registerCondition(CondStorageGuild.class,
                "%player% is [a] member of [storageguild] guild %string%",
                "%player% can manage [storageguild] guild %string%",
                "%player% has [a] [storageguild] guild invite ticket",
                "%player% has [a] [storageguild] storage expansion ticket",
                "%player% has [a] [storageguild] invite for guild %string%"
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.pattern = matchedPattern;
        player = (Expression<Player>) expressions[0];
        if (matchedPattern == 0 || matchedPattern == 1 || matchedPattern == 4) {
            guildName = (Expression<String>) expressions[1];
        }
        return true;
    }

    @Override
    public boolean check(Event event) {
        Player value = player.getSingle(event);
        if (value == null) {
            return false;
        }
        return switch (pattern) {
            case 0 -> SkriptIntegration.queries().isMember(value.getUniqueId(), guildName(event));
            case 1 -> SkriptIntegration.queries().canManage(value.getUniqueId(), guildName(event));
            case 2 -> SkriptIntegration.ticketService().hasInviteTicket(value);
            case 3 -> SkriptIntegration.ticketService().hasExpansionTicket(value);
            case 4 -> SkriptIntegration.queries().hasInvite(value.getUniqueId(), guildName(event));
            default -> false;
        };
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "storageGuild condition " + pattern;
    }

    private String guildName(Event event) {
        String value = guildName == null ? null : guildName.getSingle(event);
        return value == null ? "" : value.trim();
    }
}
