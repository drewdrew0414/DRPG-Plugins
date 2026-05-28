package com.drewdrew1.commodity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record CommodityDefinition(
        String key,
        String displayName,
        long min,
        long max,
        long defaultValue,
        long offlineIncrease,
        List<String> aliases
) {
    public CommodityDefinition {
        key = requireKey(key);
        displayName = displayName == null || displayName.isBlank() ? key : displayName.trim();
        if (max < min) {
            throw new IllegalArgumentException("max cannot be lower than min for commodity " + key);
        }
        defaultValue = clampValue(defaultValue, min, max);
        offlineIncrease = Math.max(0L, offlineIncrease);

        Set<String> normalizedAliases = new LinkedHashSet<>();
        normalizedAliases.add(normalizeToken(key));
        normalizedAliases.add(normalizeToken(displayName));
        if (aliases != null) {
            for (String alias : aliases) {
                String normalized = normalizeToken(alias);
                if (!normalized.isBlank()) {
                    normalizedAliases.add(normalized);
                }
            }
        }
        aliases = List.copyOf(normalizedAliases);
    }

    public long clamp(long value) {
        return clampValue(value, min, max);
    }

    public boolean matches(String input) {
        return aliases.contains(normalizeToken(input));
    }

    public List<String> commandAliases() {
        List<String> result = new ArrayList<>();
        result.add(key);
        result.add(displayName);
        return result;
    }

    static String normalizeToken(String input) {
        if (input == null) {
            return "";
        }
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        String trimmed = key.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("commodity key can only contain lowercase letters, numbers, and '_': " + key);
        }
        return trimmed;
    }

    private static long clampValue(long value, long min, long max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
