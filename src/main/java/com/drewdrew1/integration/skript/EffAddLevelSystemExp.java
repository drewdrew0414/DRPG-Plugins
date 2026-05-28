package com.drewdrew1.integration.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.drewdrew1.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

public final class EffAddLevelSystemExp extends Effect {
    private Expression<Number> amount;
    private Expression<Player> player;
    private Expression<String> skill;

    @Override
    protected void execute(Event event) {
        Number amountValue = amount.getSingle(event);
        Player target = player.getSingle(event);
        String skillName = skill.getSingle(event);
        if (amountValue == null || target == null || skillName == null || skillName.isBlank()) {
            return;
        }

        JavaPlugin.getPlugin(Main.class)
                .levelProgressService()
                .addRawExp(target, skillName, amountValue.doubleValue());
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean delayed, SkriptParser.ParseResult parseResult) {
        amount = (Expression<Number>) expressions[0];
        player = (Expression<Player>) expressions[1];
        skill = (Expression<String>) expressions[2];
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "add " + amount + " levelSystem experience to " + player + " for " + skill;
    }
}
