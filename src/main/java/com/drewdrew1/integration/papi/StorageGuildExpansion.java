package com.drewdrew1.integration.papi;

import com.drewdrew1.Main;
import com.drewdrew1.integration.IntegrationQueries;
import com.drewdrew1.model.Guild;
import com.drewdrew1.model.GuildMember;
import java.util.List;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class StorageGuildExpansion extends PlaceholderExpansion {
    private final Main plugin;
    private final IntegrationQueries queries;

    public StorageGuildExpansion(Main plugin, IntegrationQueries queries) {
        this.plugin = plugin;
        this.queries = queries;
    }

    @Override
    public String getIdentifier() {
        return "storageguild";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        if (player == null && requiresPlayer(key)) {
            return "";
        }

        return switch (key) {
            case "guild_count" -> Integer.toString(queries.guilds(player.getUniqueId()).size());
            case "guild_list" -> joinGuilds(queries.guilds(player.getUniqueId()));
            case "first_guild" -> queries.guilds(player.getUniqueId()).stream().findFirst().map(Guild::name).orElse("");
            case "invite_count" -> Integer.toString(queries.invites(player.getUniqueId()).size());
            case "invite_list" -> joinGuilds(queries.invites(player.getUniqueId()));
            case "personal_slots" -> Integer.toString(queries.personalStorageSlots(player.getUniqueId()));
            case "personal_pages" -> Integer.toString(queries.personalStoragePages(player.getUniqueId()));
            default -> dynamicPlaceholder(player, params, key);
        };
    }

    private String dynamicPlaceholder(OfflinePlayer player, String params, String key) {
        if (key.startsWith("guild_role_")) {
            String guildName = params.substring("guild_role_".length());
            return player == null ? "" : queries.member(player.getUniqueId(), guildName)
                    .map(member -> member.role().name())
                    .orElse("");
        }
        if (key.startsWith("guild_members_")) {
            String guildName = params.substring("guild_members_".length());
            return joinMembers(queries.members(guildName));
        }
        if (key.startsWith("guild_member_count_")) {
            String guildName = params.substring("guild_member_count_".length());
            return Integer.toString(queries.members(guildName).size());
        }
        if (key.startsWith("guild_storage_slots_")) {
            String guildName = params.substring("guild_storage_slots_".length());
            return Integer.toString(queries.guildStorageSlots(guildName));
        }
        if (key.startsWith("guild_storage_pages_")) {
            String guildName = params.substring("guild_storage_pages_".length());
            return Integer.toString(queries.guildStoragePages(guildName));
        }
        if (key.startsWith("guild_owner_")) {
            String guildName = params.substring("guild_owner_".length());
            return queries.guild(guildName).map(Guild::ownerName).orElse("");
        }
        if (key.startsWith("has_guild_")) {
            String guildName = params.substring("has_guild_".length());
            return booleanText(player != null && queries.isMember(player.getUniqueId(), guildName));
        }
        if (key.startsWith("can_manage_")) {
            String guildName = params.substring("can_manage_".length());
            return booleanText(player != null && queries.canManage(player.getUniqueId(), guildName));
        }
        if (key.startsWith("has_invite_")) {
            String guildName = params.substring("has_invite_".length());
            return booleanText(player != null && queries.hasInvite(player.getUniqueId(), guildName));
        }
        return "";
    }

    private boolean requiresPlayer(String key) {
        return key.equals("guild_count")
                || key.equals("guild_list")
                || key.equals("first_guild")
                || key.equals("invite_count")
                || key.equals("invite_list")
                || key.equals("personal_slots")
                || key.equals("personal_pages")
                || key.startsWith("guild_role_")
                || key.startsWith("has_guild_")
                || key.startsWith("can_manage_")
                || key.startsWith("has_invite_");
    }

    private String joinGuilds(List<Guild> guilds) {
        return String.join(", ", guilds.stream().map(Guild::name).toList());
    }

    private String joinMembers(List<GuildMember> members) {
        return String.join(", ", members.stream().map(GuildMember::playerName).toList());
    }

    private String booleanText(boolean value) {
        return value ? "true" : "false";
    }
}
