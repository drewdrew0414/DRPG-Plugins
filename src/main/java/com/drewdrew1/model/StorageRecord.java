package com.drewdrew1.model;

import com.drewdrew1.storage.StorageType;
import java.util.UUID;

public record StorageRecord(
        String storageKey,
        StorageType type,
        UUID ownerUuid,
        Long guildId,
        int unlockedSlots
) {
}
