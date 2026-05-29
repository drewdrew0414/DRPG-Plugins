package com.drewdrew1.integration;

import com.drewdrew1.config.StorageGuildConfig;
import com.drewdrew1.db.StorageRepository;
import com.drewdrew1.model.Guild;
import com.drewdrew1.model.GuildMember;
import com.drewdrew1.model.GuildRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class IntegrationQueries {
    private static final long TIMEOUT_MILLIS = 1500L;

    private final StorageRepository repository;
    private final StorageGuildConfig config;

    public IntegrationQueries(StorageRepository repository, StorageGuildConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public List<Guild> guilds(UUID playerUuid) {
        return await(repository.guildsForMember(playerUuid), List.of());
    }

    public List<Guild> invites(UUID playerUuid) {
        return await(repository.invitesFor(playerUuid), List.of());
    }

    public Optional<Guild> guild(String guildName) {
        if (guildName == null || guildName.isBlank()) {
            return Optional.empty();
        }
        return await(repository.findGuildByName(guildName), Optional.empty());
    }

    public List<GuildMember> members(String guildName) {
        return guild(guildName)
                .map(guild -> await(repository.members(guild.id()), List.<GuildMember>of()))
                .orElseGet(List::of);
    }

    public Optional<GuildMember> member(UUID playerUuid, String guildName) {
        return guild(guildName)
                .flatMap(guild -> await(repository.findMember(guild.id(), playerUuid), Optional.empty()));
    }

    public boolean isMember(UUID playerUuid, String guildName) {
        return member(playerUuid, guildName).isPresent();
    }

    public boolean canManage(UUID playerUuid, String guildName) {
        return member(playerUuid, guildName)
                .map(GuildMember::role)
                .map(GuildRole::canManageMembers)
                .orElse(false);
    }

    public boolean hasInvite(UUID playerUuid, String guildName) {
        return invites(playerUuid).stream().anyMatch(guild -> guild.name().equalsIgnoreCase(guildName));
    }

    public int personalStorageSlots(UUID playerUuid) {
        return await(repository.getOrCreatePersonalStorage(playerUuid), nullValue()).unlockedSlots();
    }

    public int personalStoragePages(UUID playerUuid) {
        return pages(personalStorageSlots(playerUuid));
    }

    public int guildStorageSlots(String guildName) {
        return guild(guildName)
                .map(guild -> await(repository.getOrCreateGuildStorage(guild.id()), nullValue()).unlockedSlots())
                .orElse(0);
    }

    public int guildStoragePages(String guildName) {
        return pages(guildStorageSlots(guildName));
    }

    public int pages(int slots) {
        if (slots <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(slots / (double) config.pageItemSlots()));
    }

    private <T> T await(CompletableFuture<T> future, T fallback) {
        try {
            return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private com.drewdrew1.model.StorageRecord nullValue() {
        return new com.drewdrew1.model.StorageRecord("", com.drewdrew1.storage.StorageType.PERSONAL, null, null, 0);
    }
}
