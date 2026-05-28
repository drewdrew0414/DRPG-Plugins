package com.drewdrew1.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.ExpressionType;
import com.drewdrew1.Main;

public final class SkriptIntegration {
    private static boolean registered;

    private SkriptIntegration() {
    }

    public static void register(Main plugin) {
        if (registered) {
            return;
        }

        Skript.registerExpression(
                ExprLevelSystemValue.class,
                Number.class,
                ExpressionType.COMBINED,
                "[the] (levelsystem|level system) level of %offlineplayer% for %string%",
                "[the] (levelsystem|level system) exp[erience] of %offlineplayer% for %string%",
                "[the] (levelsystem|level system) required exp[erience] of %offlineplayer% for %string%",
                "[the] (levelsystem|level system) remaining exp[erience] of %offlineplayer% for %string%",
                "[the] (levelsystem|level system) progress of %offlineplayer% for %string%",
                "[the] (levelsystem|level system) max level of %offlineplayer% for %string%"
        );
        Skript.registerExpression(
                ExprLevelSystemText.class,
                String.class,
                ExpressionType.COMBINED,
                "[the] (levelsystem|level system) display name of %string%",
                "[the] (levelsystem|level system) skill name of %string%"
        );
        Skript.registerEffect(
                EffAddLevelSystemExp.class,
                "add %number% (levelsystem|level system) exp[erience] to %player% for %string%"
        );

        registered = true;
        plugin.getLogger().info("Registered Skript expressions and effects.");
    }
}
