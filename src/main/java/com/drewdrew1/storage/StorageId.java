package com.drewdrew1.storage;

import java.util.UUID;

public record StorageId(StorageType type, String id) {
    public static StorageId personal(UUID uuid) {
        return new StorageId(StorageType.PERSONAL, uuid.toString());
    }

    public static StorageId guild(long guildId) {
        return new StorageId(StorageType.GUILD, Long.toString(guildId));
    }

    public String databaseKey() {
        return type.prefix() + ":" + id;
    }
}
