package com.drewdrew1.model;

import java.util.UUID;

public record Guild(
        long id,
        String name,
        UUID ownerUuid,
        String ownerName,
        long createdAt
) {
}
