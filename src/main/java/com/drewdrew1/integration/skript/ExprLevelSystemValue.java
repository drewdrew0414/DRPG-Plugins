package com.drewdrew1.integration.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.drewdrew1.Main;
import com.drewdrew1.config.SkillConfig;
import com.drewdrew1.progress.LevelProgressService.LevelSnapshot;
import java.util.Optional;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExprLevelSystemValue extends SimpleExpression<Number> {
    private Expression<OfflinePlayer> player;
    private Expression<String> skill;
    private ValueType valueType;

    @Override
    protected Number[] get(Event event) {
        OfflinePlayer offlinePlayer = player.getSingle(event);
        String skillName = skill.getSingle(event);
        if (offlinePlayer == null || skillName == null || skillName.isBlank()) {
            return new Number[0];
        }

        Main plugin = JavaPlugin.getPlugin(Main.class);
        Optional<SkillConfig> config = plugin.skillConfigCache().skillConfig(skillName);
        if (config.isEmpty()) {
            return new Number[0];
        }

        SkillConfig skillConfig = config.get();
        LevelSnapshot snapshot = plugin.levelProgressService()
                .cachedSnapshot(offlinePlayer.getUniqueId(), skillConfig.name())
                .orElseGet(() -> plugin.levelProgressService().defaultSnapshot(skillConfig));

        Number value = switch (valueType) {
            case LEVEL -> snapshot.level();
            case EXP -> snapshot.exp();
            case REQUIRED_EXP -> snapshot.requiredExp();
            case REMAINING_EXP -> Math.max(0.0D, snapshot.requiredExp() - snapshot.exp());
            case PROGRESS -> snapshot.progressPercent();
            case MAX_LEVEL -> snapshot.maxLevel();
        };
        return new Number[] {value};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends Number> getReturnType() {
        return Number.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean delayed, SkriptParser.ParseResult parseResult) {
        player = (Expression<OfflinePlayer>) expressions[0];
        skill = (Expression<String>) expressions[1];
        valueType = switch (matchedPattern) {
            case 0 -> ValueType.LEVEL;
            case 1 -> ValueType.EXP;
            case 2 -> ValueType.REQUIRED_EXP;
            case 3 -> ValueType.REMAINING_EXP;
            case 4 -> ValueType.PROGRESS;
            case 5 -> ValueType.MAX_LEVEL;
            default -> throw new IllegalArgumentException("Unknown levelSystem value pattern: " + matchedPattern);
        };
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "levelSystem " + valueType.name().toLowerCase() + " of " + player + " for " + skill;
    }

    private enum ValueType {
        LEVEL,
        EXP,
        REQUIRED_EXP,
        REMAINING_EXP,
        PROGRESS,
        MAX_LEVEL
    }
}
