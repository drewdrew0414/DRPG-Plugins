package com.drewdrew1.commodity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CommoditySettings {
    private final String databaseName;
    private final int offlineUnitSeconds;
    private final long maxOfflineCatchupSeconds;
    private final Map<String, CommodityDefinition> definitions;
    private final Map<String, CommodityDefinition> aliases;

    public CommoditySettings(
            String databaseName,
            int offlineUnitSeconds,
            long maxOfflineCatchupSeconds,
            List<CommodityDefinition> definitions
    ) {
        this.databaseName = databaseName == null || databaseName.isBlank() ? "rpg_commodity" : databaseName.trim();
        this.offlineUnitSeconds = Math.max(1, offlineUnitSeconds);
        this.maxOfflineCatchupSeconds = Math.max(0L, maxOfflineCatchupSeconds);

        Map<String, CommodityDefinition> byKey = new LinkedHashMap<>();
        Map<String, CommodityDefinition> byAlias = new LinkedHashMap<>();
        for (CommodityDefinition definition : definitions) {
            byKey.put(definition.key(), definition);
            for (String alias : definition.aliases()) {
                byAlias.put(alias, definition);
            }
        }
        this.definitions = Map.copyOf(byKey);
        this.aliases = Map.copyOf(byAlias);
    }

    public String databaseName() {
        return databaseName;
    }

    public int offlineUnitSeconds() {
        return offlineUnitSeconds;
    }

    public long maxOfflineCatchupSeconds() {
        return maxOfflineCatchupSeconds;
    }

    public Collection<CommodityDefinition> definitions() {
        return definitions.values();
    }

    public Optional<CommodityDefinition> find(String input) {
        if (input == null) {
            return Optional.empty();
        }
        CommodityDefinition exact = definitions.get(input.trim().toLowerCase());
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(aliases.get(CommodityDefinition.normalizeToken(input)));
    }
}
