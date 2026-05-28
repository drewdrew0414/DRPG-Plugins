package com.drewdrew1.integration.placeholder;

import com.drewdrew1.Main;
import com.drewdrew1.config.SkillConfig;
import com.drewdrew1.progress.LevelProgressService.LevelSnapshot;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class LevelPlaceholderExpansion extends PlaceholderExpansion {
    private final Main plugin;

    public LevelPlaceholderExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "levelsystem";
    }

    @Override
    public String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        return authors.isEmpty() ? "drewdrew1" : String.join(", ", authors);
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
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isBlank()) {
            return "";
        }

        String normalizedParams = params.toLowerCase(Locale.ROOT);
        if (normalizedParams.equals("skills")) {
            return plugin.skillConfigCache().skillConfigs().stream()
                    .map(SkillConfig::name)
                    .collect(Collectors.joining(","));
        }

        PlaceholderRequest request = parse(params);
        if (request == null) {
            return "";
        }

        Optional<SkillConfig> config = plugin.skillConfigCache().skillConfig(request.skillName());
        if (config.isEmpty()) {
            return "";
        }

        SkillConfig skillConfig = config.get();
        if (request.type() == PlaceholderType.DISPLAY) {
            return displayName(skillConfig);
        }
        if (request.type() == PlaceholderType.SKILL) {
            return skillConfig.name();
        }
        if (request.type() == PlaceholderType.MAX_LEVEL && player == null) {
            return Integer.toString(skillConfig.maxLevel());
        }
        if (player == null) {
            return "";
        }

        LevelSnapshot snapshot = plugin.levelProgressService()
                .cachedSnapshot(player.getUniqueId(), skillConfig.name())
                .orElseGet(() -> plugin.levelProgressService().defaultSnapshot(skillConfig));

        return switch (request.type()) {
            case LEVEL -> Integer.toString(snapshot.level());
            case EXP -> format(snapshot.exp());
            case REQUIRED_EXP -> format(snapshot.requiredExp());
            case REMAINING_EXP -> format(Math.max(0.0D, snapshot.requiredExp() - snapshot.exp()));
            case PROGRESS, PERCENT -> format(snapshot.progressPercent());
            case MAX_LEVEL -> Integer.toString(snapshot.maxLevel());
            case DISPLAY -> snapshot.displayName();
            case SKILL -> snapshot.skill();
        };
    }

    private PlaceholderRequest parse(String params) {
        for (PlaceholderType type : PlaceholderType.values()) {
            for (String prefix : type.prefixes()) {
                if (params.length() > prefix.length() && params.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    return new PlaceholderRequest(type, params.substring(prefix.length()));
                }
            }
        }
        return null;
    }

    private String displayName(SkillConfig config) {
        String displayName = config.displayName();
        if (displayName == null || displayName.isBlank()) {
            return config.name();
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', displayName));
    }

    private String format(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.000001D) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private record PlaceholderRequest(PlaceholderType type, String skillName) {
    }

    private enum PlaceholderType {
        REQUIRED_EXP("required_exp_", "required_"),
        REMAINING_EXP("remaining_exp_", "remaining_"),
        MAX_LEVEL("max_level_", "max_"),
        DISPLAY("display_"),
        PROGRESS("progress_"),
        PERCENT("percent_"),
        LEVEL("level_"),
        EXP("exp_"),
        SKILL("skill_");

        private final List<String> prefixes;

        PlaceholderType(String... prefixes) {
            this.prefixes = List.of(prefixes);
        }

        private List<String> prefixes() {
            return prefixes;
        }
    }
}
