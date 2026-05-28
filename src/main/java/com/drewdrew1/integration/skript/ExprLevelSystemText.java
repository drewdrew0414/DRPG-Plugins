package com.drewdrew1.integration.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.drewdrew1.Main;
import com.drewdrew1.config.SkillConfig;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExprLevelSystemText extends SimpleExpression<String> {
    private Expression<String> skill;
    private TextType textType;

    @Override
    protected String[] get(Event event) {
        String skillName = skill.getSingle(event);
        if (skillName == null || skillName.isBlank()) {
            return new String[0];
        }

        Main plugin = JavaPlugin.getPlugin(Main.class);
        Optional<SkillConfig> config = plugin.skillConfigCache().skillConfig(skillName);
        if (config.isEmpty()) {
            return new String[0];
        }

        SkillConfig skillConfig = config.get();
        String value = switch (textType) {
            case DISPLAY -> displayName(skillConfig);
            case SKILL -> skillConfig.name();
        };
        return new String[] {value};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean delayed, SkriptParser.ParseResult parseResult) {
        skill = (Expression<String>) expressions[0];
        textType = matchedPattern == 0 ? TextType.DISPLAY : TextType.SKILL;
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "levelSystem " + textType.name().toLowerCase() + " for " + skill;
    }

    private String displayName(SkillConfig config) {
        String displayName = config.displayName();
        if (displayName == null || displayName.isBlank()) {
            return config.name();
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', displayName));
    }

    private enum TextType {
        DISPLAY,
        SKILL
    }
}
