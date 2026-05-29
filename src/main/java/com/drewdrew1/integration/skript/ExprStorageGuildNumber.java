package com.drewdrew1.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public final class ExprStorageGuildNumber extends SimpleExpression<Number> {
    private int pattern;
    private Expression<Player> player;
    private Expression<String> guildName;

    static void register() {
        Skript.registerExpression(ExprStorageGuildNumber.class, Number.class, ExpressionType.SIMPLE,
                "[storageguild] guild count of %player%",
                "[storageguild] invite count of %player%",
                "[storageguild] personal storage slots of %player%",
                "[storageguild] personal storage pages of %player%",
                "[storageguild] guild storage slots of guild %string%",
                "[storageguild] guild storage pages of guild %string%",
                "[storageguild] member count of guild %string%"
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.pattern = matchedPattern;
        if (matchedPattern <= 3) {
            player = (Expression<Player>) expressions[0];
        } else {
            guildName = (Expression<String>) expressions[0];
        }
        return true;
    }

    @Override
    protected Number[] get(Event event) {
        Player value = player(event);
        Number number = switch (pattern) {
            case 0 -> value == null ? 0 : SkriptIntegration.queries().guilds(value.getUniqueId()).size();
            case 1 -> value == null ? 0 : SkriptIntegration.queries().invites(value.getUniqueId()).size();
            case 2 -> value == null ? 0 : SkriptIntegration.queries().personalStorageSlots(value.getUniqueId());
            case 3 -> value == null ? 0 : SkriptIntegration.queries().personalStoragePages(value.getUniqueId());
            case 4 -> SkriptIntegration.queries().guildStorageSlots(guildName(event));
            case 5 -> SkriptIntegration.queries().guildStoragePages(guildName(event));
            case 6 -> SkriptIntegration.queries().members(guildName(event)).size();
            default -> 0;
        };
        return new Number[]{number};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends Number> getReturnType() {
        return Number.class;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "storageGuild number expression " + pattern;
    }

    private Player player(Event event) {
        return player == null ? null : player.getSingle(event);
    }

    private String guildName(Event event) {
        String value = guildName == null ? null : guildName.getSingle(event);
        return value == null ? "" : value.trim();
    }
}
