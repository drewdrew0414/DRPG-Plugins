package com.drewdrew1.commodity;

import java.util.Map;
import java.util.UUID;

public record CommodityChangeResult(
        UUID playerId,
        String playerName,
        String commodityKey,
        long oldAmount,
        long newAmount,
        Map<String, Long> amounts
) {
    public CommodityChangeResult {
        amounts = Map.copyOf(amounts);
    }
}
