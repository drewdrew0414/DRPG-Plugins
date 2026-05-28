package com.drewdrew1.model;

import java.util.UUID;

public record GuildMember(
        long guildId,
        UUID playerUuid,
        String playerName,
        GuildRole role,
        long joinedAt
) {
}
