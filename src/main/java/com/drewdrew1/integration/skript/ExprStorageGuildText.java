package com.drewdrew1.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.drewdrew1.model.Guild;
import com.drewdrew1.model.GuildMember;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public final class ExprStorageGuildText extends SimpleExpression<String> {
    private int pattern;
    private Expression<Player> player;
    private Expression<String> guildName;

    static void register() {
        Skript.registerExpression(ExprStorageGuildText.class, String.class, ExpressionType.SIMPLE,
                "[storageguild] guilds of %player%",
                "[storageguild] invite guilds of %player%",
                "[storageguild] role of %player% in guild %string%",
                "[storageguild] members of guild %string%",
                "[storageguild] owner of guild %string%"
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.pattern = matchedPattern;
        switch (matchedPattern) {
            case 0, 1 -> player = (Expression<Player>) expressions[0];
            case 2 -> {
                player = (Expression<Player>) expressions[0];
                guildName = (Expression<String>) expressions[1];
            }
            case 3, 4 -> guildName = (Expression<String>) expressions[0];
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected String[] get(Event event) {
        return switch (pattern) {
            case 0 -> guilds(event);
            case 1 -> inviteGuilds(event);
            case 2 -> role(event);
            case 3 -> members(event);
            case 4 -> owner(event);
            default -> new String[0];
        };
    }

    @Override
    public boolean isSingle() {
        return pattern == 2 || pattern == 4;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "storageGuild text expression " + pattern;
    }

    private String[] guilds(Event event) {
        Player value = player(event);
        if (value == null) {
            return new String[0];
        }
        return SkriptIntegration.queries().guilds(value.getUniqueId()).stream().map(Guild::name).toArray(String[]::new);
    }

    private String[] inviteGuilds(Event event) {
        Player value = player(event);
        if (value == null) {
            return new String[0];
        }
        return SkriptIntegration.queries().invites(value.getUniqueId()).stream().map(Guild::name).toArray(String[]::new);
    }

    private String[] role(Event event) {
        Player value = player(event);
        if (value == null) {
            return new String[0];
        }
        return SkriptIntegration.queries()
                .member(value.getUniqueId(), guildName(event))
                .map(member -> new String[]{member.role().name()})
                .orElseGet(() -> new String[0]);
    }

    private String[] members(Event event) {
        return SkriptIntegration.queries().members(guildName(event)).stream().map(GuildMember::playerName).toArray(String[]::new);
    }

    private String[] owner(Event event) {
        return SkriptIntegration.queries()
                .guild(guildName(event))
                .map(guild -> new String[]{guild.ownerName()})
                .orElseGet(() -> new String[0]);
    }

    private Player player(Event event) {
        return player == null ? null : player.getSingle(event);
    }

    private String guildName(Event event) {
        String value = guildName == null ? null : guildName.getSingle(event);
        return value == null ? "" : value.trim();
    }
}
