package com.drewdrew1.commodity;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CommodityPlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final CommodityService service;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.KOREA);

    public CommodityPlaceholderExpansion(JavaPlugin plugin, CommodityService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public String getIdentifier() {
        return "commodity";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return plugin.isEnabled();
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isBlank()) {
            return "";
        }

        String raw = params.trim();
        if ("list".equalsIgnoreCase(raw)) {
            return service.settings().definitions().stream()
                    .map(CommodityDefinition::key)
                    .collect(Collectors.joining(","));
        }

        PlaceholderMode mode = PlaceholderMode.AMOUNT;
        String commodityToken = raw;
        for (PlaceholderMode candidate : PlaceholderMode.values()) {
            if (candidate.suffix().isEmpty()) {
                continue;
            }
            if (raw.toLowerCase(Locale.ROOT).endsWith(candidate.suffix())) {
                mode = candidate;
                commodityToken = raw.substring(0, raw.length() - candidate.suffix().length());
                break;
            }
        }

        CommodityDefinition definition = service.settings().find(commodityToken).orElse(null);
        if (definition == null) {
            return "";
        }

        long amount = player == null ? definition.defaultValue() : service.placeholderAmount(player, definition);
        return switch (mode) {
            case AMOUNT -> Long.toString(amount);
            case FORMATTED -> numberFormat.format(amount);
            case MIN -> Long.toString(definition.min());
            case MAX -> Long.toString(definition.max());
            case DEFAULT -> Long.toString(definition.defaultValue());
            case REMAINING -> Long.toString(Math.max(0L, definition.max() - amount));
            case DISPLAY -> definition.displayName();
            case OFFLINE_INCREASE -> Long.toString(definition.offlineIncrease());
        };
    }

    private enum PlaceholderMode {
        FORMATTED("_formatted"),
        MIN("_min"),
        MAX("_max"),
        DEFAULT("_default"),
        REMAINING("_remaining"),
        DISPLAY("_display"),
        OFFLINE_INCREASE("_offline_increase"),
        AMOUNT("");

        private final String suffix;

        PlaceholderMode(String suffix) {
            this.suffix = suffix;
        }

        String suffix() {
            return suffix;
        }
    }
}
