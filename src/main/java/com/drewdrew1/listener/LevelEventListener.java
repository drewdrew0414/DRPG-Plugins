package com.drewdrew1.listener;

import com.drewdrew1.Main;
import com.drewdrew1.config.SkillConfig;
import com.drewdrew1.progress.LevelProgressService;
import com.drewdrew1.progress.PlacedBlockTracker;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

public final class LevelEventListener implements Listener {
    private static final String PROJECTILE_WEAPON_METADATA = "levelsystem_weapon";

    private final Main plugin;
    private final LevelProgressService progressService;
    private final PlacedBlockTracker placedBlockTracker;

    public LevelEventListener(Main plugin, LevelProgressService progressService, PlacedBlockTracker placedBlockTracker) {
        this.plugin = plugin;
        this.progressService = progressService;
        this.placedBlockTracker = placedBlockTracker;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldTrackPlacedBlock(event.getBlock().getType())) {
            placedBlockTracker.markPlaced(event.getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean wasPlacedByPlayer = placedBlockTracker.consumeIfPlaced(block);
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();

        forEachRule("breakBlock", (config, rule) -> {
            if (!matchesMaterial(rule.block(), block.getType())) {
                return;
            }
            if (!matchesTool(rule.useTool(), tool.getType())) {
                return;
            }
            if (!matchesRequiredEnchants(rule.requiredEnchant(), tool)) {
                return;
            }
            if (!matchesWorld(rule.world(), block.getWorld())) {
                return;
            }
            if (!matchesBiome(rule.biome(), block)) {
                return;
            }
            if (!rule.placedByPlayer() && wasPlacedByPlayer) {
                return;
            }

            progressService.addExp(event.getPlayer(), config, rule, block.getType().name());
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }

        event.getEntity().setMetadata(
                PROJECTILE_WEAPON_METADATA,
                new FixedMetadataValue(plugin, projectileWeaponAtLaunch(player))
        );
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity deadEntity = event.getEntity();
        Entity causingEntity = event.getDamageSource().getCausingEntity();
        Entity directEntity = event.getDamageSource().getDirectEntity();

        Player player = causingEntity instanceof Player causingPlayer ? causingPlayer : null;
        if (player == null && directEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            player = shooter;
        }
        if (player == null) {
            return;
        }

        Player killer = player;
        boolean ranged = event.getDamageSource().isIndirect() || directEntity instanceof Projectile;
        String action = ranged ? "rangedKill" : "killEntity";
        ItemStack currentTool = killer.getInventory().getItemInMainHand();
        ItemStack tool = directEntity instanceof Projectile projectile
                ? projectileWeapon(projectile).orElse(currentTool)
                : currentTool;

        forEachRule(action, (config, rule) -> {
            if (!matchesMaterial(rule.entity(), deadEntity.getType().name())) {
                return;
            }
            if (!matchesTool(rule.useTool(), tool.getType())) {
                return;
            }
            if (!matchesRequiredEnchants(rule.requiredEnchant(), tool)) {
                return;
            }

            progressService.addExp(killer, config, rule, deadEntity.getType().name());
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) {
            return;
        }

        Material material = item.getItemStack().getType();
        forEachRule("fishCatch", (config, rule) -> {
            if (!matchesMaterial(rule.item(), material)) {
                return;
            }

            progressService.addExp(event.getPlayer(), config, rule, material.name());
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        Material material = result.getType();
        forEachRule("alchemyCraft", (config, rule) -> {
            if (!matchesMaterial(rule.targetItem(), material)) {
                return;
            }

            progressService.addExp(player, config, rule, material.name());
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBrewResultTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getType() != InventoryType.BREWING) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 3) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || !isPotion(currentItem.getType()) || !(currentItem.getItemMeta() instanceof PotionMeta potionMeta)) {
            return;
        }

        String potionName = potionMeta.getBasePotionType() == null
                ? currentItem.getType().name()
                : potionMeta.getBasePotionType().getKey().getKey();
        int slot = event.getRawSlot();
        int beforeAmount = currentItem.getAmount();
        ItemStack beforeItem = currentItem.clone();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack afterItem = topInventory.getItem(slot);
            int afterAmount = afterItem == null || !afterItem.isSimilar(beforeItem) ? 0 : afterItem.getAmount();
            int removedAmount = beforeAmount - afterAmount;
            if (removedAmount <= 0) {
                return;
            }

            forEachRule("craftPotion", (config, rule) -> {
                if (!matchesPotion(rule, potionName)) {
                    return;
                }

                for (int index = 0; index < removedAmount; index++) {
                    progressService.addExp(player, config, rule, normalize(potionName));
                }
            });
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        progressService.deliverEarnedRewards(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        progressService.unloadPlayer(event.getPlayer().getUniqueId());
    }

    private void forEachRule(String action, RuleConsumer consumer) {
        for (SkillConfig config : plugin.skillConfigCache().skillConfigs()) {
            for (Map<String, List<SkillConfig.ExpRule>> group : config.getExp()) {
                List<SkillConfig.ExpRule> rules = group.get(action);
                if (rules == null) {
                    continue;
                }

                for (SkillConfig.ExpRule rule : rules) {
                    consumer.accept(config, rule);
                }
            }
        }
    }

    private boolean matchesMaterial(List<String> expected, Material actual) {
        return matchesMaterial(expected, actual.name());
    }

    private boolean matchesMaterial(String expected, Material actual) {
        return expected != null && normalize(expected).equals(normalize(actual.name()));
    }

    private boolean matchesMaterial(List<String> expected, String actual) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }

        String normalizedActual = normalize(actual);
        return expected.stream().map(this::normalize).anyMatch(normalizedActual::equals);
    }

    private boolean matchesTool(List<String> expected, Material actual) {
        return matchesTool(expected, actual.name());
    }

    private boolean matchesTool(List<String> expected, String actual) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }

        String materialName = normalize(actual);
        for (String value : expected) {
            String tool = normalize(value);
            if (tool.equals(materialName)) {
                return true;
            }
            if (tool.equals("PICKAXE") && materialName.endsWith("_PICKAXE")) {
                return true;
            }
            if (tool.equals("AXE") && materialName.endsWith("_AXE") && !materialName.endsWith("_PICKAXE")) {
                return true;
            }
            if (tool.equals("HOE") && materialName.endsWith("_HOE")) {
                return true;
            }
            if (tool.equals("SWORD") && materialName.endsWith("_SWORD")) {
                return true;
            }
            if (tool.equals("BOW") && materialName.equals("BOW")) {
                return true;
            }
            if (tool.equals("CROSSBOW") && materialName.equals("CROSSBOW")) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRequiredEnchants(List<String> expected, ItemStack itemStack) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (itemStack == null || itemStack.getEnchantments().isEmpty()) {
            return false;
        }

        return expected.stream().allMatch(required -> itemStack.getEnchantments().keySet().stream()
                .anyMatch(enchantment -> enchantmentMatches(required, enchantment.getKey().getKey())));
    }

    private boolean enchantmentMatches(String expected, String actual) {
        String normalizedExpected = normalize(expected).replace('-', '_');
        String normalizedActual = normalize(actual).replace('-', '_');
        return normalizedExpected.equals(normalizedActual);
    }

    private boolean matchesWorld(List<String> expected, World world) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }

        String environment = switch (world.getEnvironment()) {
            case NORMAL -> "OVERWORLD";
            case NETHER -> "NETHER";
            case THE_END -> "THE_END";
            case CUSTOM -> "CUSTOM";
        };

        return expected.stream().map(this::normalize).anyMatch(value ->
                value.equals(normalize(environment)) || value.equals(normalize(world.getName())));
    }

    private boolean matchesBiome(List<String> expected, Block block) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }

        String biomeKey = block.getBiome().getKey().getKey();
        return expected.stream().map(this::normalize).anyMatch(value -> value.equals(normalize(biomeKey)));
    }

    private boolean matchesPotion(SkillConfig.ExpRule rule, String potionName) {
        String normalizedPotion = normalize(potionName);
        if (!rule.potion().isEmpty()) {
            return rule.potion().stream()
                    .map(this::normalize)
                    .anyMatch(expected -> potionAliasMatches(expected, normalizedPotion));
        }

        String potionType = normalize(rule.potionType());
        if (potionType.isBlank()) {
            return true;
        }
        if (potionType.equals("AWKWARD")) {
            return normalizedPotion.equals("AWKWARD");
        }
        if (potionType.equals("ENHANCED_POTION")) {
            return normalizedPotion.startsWith("STRONG_");
        }
        if (potionType.equals("EXTENDED_POTION")) {
            return normalizedPotion.startsWith("LONG_");
        }
        return !normalizedPotion.equals("AWKWARD");
    }

    private boolean potionAliasMatches(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        if (expected.endsWith("_II") && actual.equals("STRONG_" + expected.substring(0, expected.length() - 3))) {
            return true;
        }
        String compactExpected = expected.replace("_", "");
        String compactActual = actual.replace("_", "");
        return compactExpected.contains(compactActual) || compactActual.contains(compactExpected);
    }

    private boolean isPotion(Material material) {
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
    }

    private boolean shouldTrackPlacedBlock(Material material) {
        for (SkillConfig config : plugin.skillConfigCache().skillConfigs()) {
            for (Map<String, List<SkillConfig.ExpRule>> group : config.getExp()) {
                List<SkillConfig.ExpRule> rules = group.get("breakBlock");
                if (rules == null) {
                    continue;
                }
                for (SkillConfig.ExpRule rule : rules) {
                    if (!rule.placedByPlayer() && matchesMaterial(rule.block(), material)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Optional<ItemStack> projectileWeapon(Projectile projectile) {
        for (MetadataValue metadataValue : projectile.getMetadata(PROJECTILE_WEAPON_METADATA)) {
            if (metadataValue.getOwningPlugin() == plugin) {
                Object value = metadataValue.value();
                if (value instanceof ItemStack itemStack) {
                    return Optional.of(itemStack);
                }
            }
        }
        return Optional.empty();
    }

    private ItemStack projectileWeaponAtLaunch(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.BOW || mainHand.getType() == Material.CROSSBOW) {
            return mainHand.clone();
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.BOW || offHand.getType() == Material.CROSSBOW) {
            return offHand.clone();
        }

        return mainHand.clone();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface RuleConsumer {
        void accept(SkillConfig config, SkillConfig.ExpRule rule);
    }

}
