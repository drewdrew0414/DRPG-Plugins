package com.drewdrew1.model;

import java.util.UUID;

public record GuildInvite(
        long guildId,
        UUID inviterUuid,
        UUID targetUuid,
        String targetName,
        long createdAt,
        long expiresAt
) {
    public boolean expired(long now) {
        return expiresAt <= now;
    }
}
