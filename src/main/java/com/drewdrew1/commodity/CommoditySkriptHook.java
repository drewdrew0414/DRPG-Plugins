package com.drewdrew1.commodity;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CommoditySkriptHook {
    private static CommodityService service;
    private static boolean registered;

    private CommoditySkriptHook() {
    }

    public static void register(JavaPlugin plugin, CommodityService commodityService) {
        if (registered) {
            return;
        }
        service = commodityService;
        Skript.registerAddon(plugin);
        Skript.registerExpression(
                ExprCommodity.class,
                Number.class,
                ExpressionType.COMBINED,
                "[rpg] commodity %string% of %offlineplayer%",
                "[rpg] %offlineplayer%'s commodity %string%"
        );
        Skript.registerEffect(
                EffCommodityModify.class,
                "add %number% to [rpg] commodity %string% of %offlineplayer%",
                "give %number% to [rpg] commodity %string% of %offlineplayer%",
                "remove %number% from [rpg] commodity %string% of %offlineplayer%",
                "subtract %number% from [rpg] commodity %string% of %offlineplayer%",
                "take %number% from [rpg] commodity %string% of %offlineplayer%",
                "set [rpg] commodity %string% of %offlineplayer% to %number%",
                "reset [rpg] commodity %string% of %offlineplayer%"
        );
        registered = true;
        plugin.getLogger().info("Skript commodity syntax is enabled.");
    }

    public static final class ExprCommodity extends SimpleExpression<Number> {
        private Expression<String> commodityExpression;
        private Expression<OfflinePlayer> playerExpression;

        @Override
        @SuppressWarnings("unchecked")
        public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            if (matchedPattern == 0) {
                commodityExpression = (Expression<String>) expressions[0];
                playerExpression = (Expression<OfflinePlayer>) expressions[1];
            } else {
                playerExpression = (Expression<OfflinePlayer>) expressions[0];
                commodityExpression = (Expression<String>) expressions[1];
            }
            return true;
        }

        @Override
        protected Number[] get(Event event) {
            OfflinePlayer player = playerExpression.getSingle(event);
            String commodity = commodityExpression.getSingle(event);
            if (player == null || commodity == null || service == null) {
                return new Number[0];
            }

            CommodityDefinition definition = service.settings().find(commodity).orElse(null);
            if (definition == null) {
                return new Number[0];
            }
            return new Number[]{service.placeholderAmount(player, definition)};
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
        public String toString(Event event, boolean debug) {
            return "rpg commodity";
        }
    }

    public static final class EffCommodityModify extends Effect {
        private Expression<Number> amountExpression;
        private Expression<String> commodityExpression;
        private Expression<OfflinePlayer> playerExpression;
        private CommodityOperation operation;

        @Override
        @SuppressWarnings("unchecked")
        public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            operation = switch (matchedPattern) {
                case 0, 1 -> CommodityOperation.ADD;
                case 2, 3, 4 -> CommodityOperation.SUBTRACT;
                case 5 -> CommodityOperation.SET;
                case 6 -> CommodityOperation.RESET;
                default -> CommodityOperation.ADD;
            };

            if (matchedPattern <= 4) {
                amountExpression = (Expression<Number>) expressions[0];
                commodityExpression = (Expression<String>) expressions[1];
                playerExpression = (Expression<OfflinePlayer>) expressions[2];
            } else if (matchedPattern == 5) {
                commodityExpression = (Expression<String>) expressions[0];
                playerExpression = (Expression<OfflinePlayer>) expressions[1];
                amountExpression = (Expression<Number>) expressions[2];
            } else {
                commodityExpression = (Expression<String>) expressions[0];
                playerExpression = (Expression<OfflinePlayer>) expressions[1];
            }
            return true;
        }

        @Override
        protected void execute(Event event) {
            if (service == null) {
                return;
            }
            OfflinePlayer player = playerExpression.getSingle(event);
            String commodity = commodityExpression.getSingle(event);
            if (player == null || commodity == null) {
                return;
            }
            CommodityDefinition definition = service.settings().find(commodity).orElse(null);
            if (definition == null) {
                return;
            }

            long amount = 0L;
            if (operation != CommodityOperation.RESET) {
                Number number = amountExpression.getSingle(event);
                if (number == null) {
                    return;
                }
                amount = number.longValue();
            }

            service.change(player, player.getName(), definition, operation, amount)
                    .exceptionally(error -> {
                        BukkitLoggerHolder.LOGGER.log(Level.WARNING, "Skript commodity effect failed.", CommodityService.unwrap(error));
                        return null;
                    });
        }

        @Override
        public String toString(Event event, boolean debug) {
            return "rpg commodity modification";
        }
    }

    private static final class BukkitLoggerHolder {
        private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("RPGCommodity");
    }
}
