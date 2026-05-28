package com.drewdrew1.integration.placeholder;

import com.drewdrew1.Main;

public final class PlaceholderApiIntegration {
    private PlaceholderApiIntegration() {
    }

    public static Runnable register(Main plugin) {
        LevelPlaceholderExpansion expansion = new LevelPlaceholderExpansion(plugin);
        if (!expansion.register()) {
            plugin.getLogger().warning("Failed to register PlaceholderAPI expansion.");
            return () -> {
            };
        }

        plugin.getLogger().info("Registered PlaceholderAPI expansion: %levelsystem_*%");
        return expansion::unregister;
    }
}
