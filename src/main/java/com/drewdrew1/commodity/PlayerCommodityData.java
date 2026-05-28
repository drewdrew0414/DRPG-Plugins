package com.drewdrew1.commodity;

import java.util.Map;
import java.util.UUID;

public record PlayerCommodityData(UUID playerId, String playerName, Map<String, Long> amounts) {
    public PlayerCommodityData {
        amounts = Map.copyOf(amounts);
    }

    public long amount(String key, long fallback) {
        return amounts.getOrDefault(key, fallback);
    }
}
