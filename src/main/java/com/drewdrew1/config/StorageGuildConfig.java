package com.drewdrew1.config;

import org.bukkit.configuration.file.FileConfiguration;

public record StorageGuildConfig(
        int defaultStorageSlots,
        int slotsPerExpansion,
        int guiSize,
        int pageItemSlots,
        int maxPages,
        int inviteExpireMinutes,
        int maxGuildNameLength
) {
    public static StorageGuildConfig load(FileConfiguration config) {
        config.addDefault("storage.default-slots", 9);
        config.addDefault("storage.slots-per-expansion", 9);
        config.addDefault("storage.gui-size", 54);
        config.addDefault("storage.page-item-slots", 45);
        config.addDefault("storage.max-pages", 20);
        config.addDefault("guild.invite-expire-minutes", 10);
        config.addDefault("guild.max-name-length", 16);
        config.options().copyDefaults(true);

        int guiSize = normalizeMultipleOfNine(config.getInt("storage.gui-size", 54), 54);
        int pageItemSlots = Math.min(config.getInt("storage.page-item-slots", 45), guiSize - 9);
        pageItemSlots = Math.max(9, normalizeMultipleOfNine(pageItemSlots, 45));

        return new StorageGuildConfig(
                Math.max(9, normalizeMultipleOfNine(config.getInt("storage.default-slots", 9), 9)),
                Math.max(9, normalizeMultipleOfNine(config.getInt("storage.slots-per-expansion", 9), 9)),
                guiSize,
                pageItemSlots,
                Math.max(1, config.getInt("storage.max-pages", 20)),
                Math.max(1, config.getInt("guild.invite-expire-minutes", 10)),
                Math.max(3, config.getInt("guild.max-name-length", 16))
        );
    }

    public int maxStorageSlots() {
        return pageItemSlots * maxPages;
    }

    private static int normalizeMultipleOfNine(int value, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(9, (value / 9) * 9);
    }
}
