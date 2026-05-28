package com.drewdrew1.progress;

import com.drewdrew1.LevelDatabase;
import com.drewdrew1.Main;
import com.drewdrew1.config.SkillConfig;
import com.drewdrew1.config.SkillConfigCache;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class LevelProgressService {
    private static final long COMBO_TIMEOUT_MILLIS = 10_000L;

    private final Main plugin;
    private final LevelDatabase levelDatabase;
    private final SkillConfigCache skillConfigCache;
    private final Gson gson = new Gson();
    private final NamespacedKey untradeableKey;
    private final Map<SkillKey, CompletableFuture<PlayerSkillState>> states = new ConcurrentHashMap<>();
    private final Map<ComboKey, ComboState> combos = new ConcurrentHashMap<>();

    public LevelProgressService(Main plugin, LevelDatabase levelDatabase, SkillConfigCache skillConfigCache) {
        this.plugin = plugin;
        this.levelDatabase = levelDatabase;
        this.skillConfigCache = skillConfigCache;
        this.untradeableKey = new NamespacedKey(plugin, "untradeable");
    }

    public void addExp(Player player, SkillConfig config, SkillConfig.ExpRule rule, String comboTarget) {
        if (config.name() == null || config.name().isBlank()) {
            return;
        }

        SkillKey key = new SkillKey(player.getUniqueId(), normalize(config.name()));
        CompletableFuture<PlayerSkillState> stateFuture = states.computeIfAbsent(key, unused -> loadState(key, config));
        stateFuture.whenComplete((state, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING, "Failed to load level data for " + player.getName(), throwable);
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = plugin.getServer().getPlayer(player.getUniqueId());
                if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                    return;
                }
                applyExp(onlinePlayer, config, rule, comboTarget, state);
            });
        });
    }

    public void removePlayer(UUID uuid) {
        states.keySet().removeIf(key -> key.uuid().equals(uuid));
        combos.keySet().removeIf(key -> key.uuid().equals(uuid));
    }

    public CompletableFuture<Void> flush() {
        CompletableFuture<?>[] saves = states.values().stream()
                .filter(CompletableFuture::isDone)
                .map(future -> future.thenCompose(PlayerSkillState::saveChain))
                .toArray(CompletableFuture[]::new);

        if (saves.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(saves);
    }

    private CompletableFuture<PlayerSkillState> loadState(SkillKey key, SkillConfig config) {
        return levelDatabase.loadLevel(key.uuid(), config.name())
                .thenApply(data -> data
                        .map(levelData -> new PlayerSkillState(
                                key.uuid(),
                                config.name(),
                                clamp(levelData.level(), config.startLevel(), config.maxLevel()),
                                Math.max(0.0D, levelData.exp())
                        ))
                        .orElseGet(() -> new PlayerSkillState(
                                key.uuid(),
                                config.name(),
                                config.startLevel(),
                                0.0D
                        )));
    }

    private void applyExp(
            Player player,
            SkillConfig config,
            SkillConfig.ExpRule rule,
            String comboTarget,
            PlayerSkillState state
    ) {
        if (state.level() < rule.requiredLevel() || state.level() >= config.maxLevel()) {
            return;
        }

        double gainedExp = calculateExp(player.getUniqueId(), config, rule, comboTarget);
        if (gainedExp <= 0) {
            return;
        }

        int beforeLevel = state.level();
        double exp = state.exp() + gainedExp;
        int level = state.level();
        while (level < config.maxLevel()) {
            double requiredExp = requiredExp(level);
            if (exp < requiredExp) {
                break;
            }
            exp -= requiredExp;
            level++;
        }

        if (level >= config.maxLevel()) {
            level = config.maxLevel();
            exp = 0.0D;
        }

        state.update(level, exp);
        enqueueSave(state);

        if (level > beforeLevel) {
            player.sendMessage(ChatColor.GREEN + config.displayName() + " Lv." + level + " 달성!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.2F);
            giveReachedRewards(player, config, beforeLevel, level);
        }
    }

    private double calculateExp(UUID uuid, SkillConfig config, SkillConfig.ExpRule rule, String comboTarget) {
        double result = randomRange(firstRange(rule.getExp()));
        if (result <= 0) {
            return 0.0D;
        }

        for (SkillConfig.ValueRange bonusRange : rule.bonusExp()) {
            if (ThreadLocalRandom.current().nextDouble() <= bonusRange.chance()) {
                result *= randomRange(bonusRange);
            }
        }

        result *= Math.max(1.0D, config.multipleExp());
        result *= comboMultiplier(uuid, config.name(), comboTarget, rule.combo());
        return result;
    }

    private SkillConfig.ValueRange firstRange(List<SkillConfig.ValueRange> ranges) {
        return ranges.isEmpty() ? null : ranges.get(0);
    }

    private double randomRange(SkillConfig.ValueRange range) {
        if (range == null) {
            return 0.0D;
        }

        double min = Math.min(range.min(), range.max());
        double max = Math.max(range.min(), range.max());
        if (Double.compare(min, max) == 0) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private double comboMultiplier(
            UUID uuid,
            String skill,
            String target,
            List<SkillConfig.ComboConfig> comboConfigs
    ) {
        if (comboConfigs.isEmpty()) {
            return 1.0D;
        }

        long now = System.currentTimeMillis();
        ComboKey key = new ComboKey(uuid, normalize(skill), normalize(target));
        ComboState state = combos.compute(key, (unused, existing) -> {
            if (existing == null || now - existing.lastUpdated() > COMBO_TIMEOUT_MILLIS) {
                return new ComboState(1, now);
            }
            return new ComboState(existing.count() + 1, now);
        });

        return comboConfigs.stream()
                .filter(combo -> state.count() >= combo.count())
                .max(Comparator.comparingInt(SkillConfig.ComboConfig::count))
                .map(SkillConfig.ComboConfig::multiply)
                .orElse(1.0D);
    }

    private void giveReachedRewards(Player player, SkillConfig config, int beforeLevel, int afterLevel) {
        for (SkillConfig.RewardConfig reward : config.getItem()) {
            if (reward.level() <= beforeLevel || reward.level() > afterLevel) {
                continue;
            }

            levelDatabase.claimRewardIfAbsent(player.getUniqueId(), config.name(), reward.level())
                    .thenAccept(claimed -> {
                        if (!claimed) {
                            return;
                        }

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            Player onlinePlayer = plugin.getServer().getPlayer(player.getUniqueId());
                            if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                                return;
                            }

                            giveReward(onlinePlayer, config, reward);
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.WARNING, "Failed to claim level reward.", throwable);
                        return null;
                    });
        }
    }

    private void giveReward(Player player, SkillConfig config, SkillConfig.RewardConfig reward) {
        List<ItemStack> itemStacks = new ArrayList<>();
        if (reward.isRandom()) {
            List<SkillConfig.RandomItemConfig> randomItems = reward.randomItem();
            if (!randomItems.isEmpty()) {
                SkillConfig.RandomItemConfig selected = randomItems.get(ThreadLocalRandom.current().nextInt(randomItems.size()));
                itemStacks.addAll(itemStacks(selected.item()));
            }
        } else {
            for (SkillConfig.ItemStackConfig itemConfig : reward.item()) {
                itemStack(itemConfig).ifPresent(itemStacks::add);
            }
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStacks.toArray(ItemStack[]::new));
        leftovers.values().forEach(itemStack -> player.getWorld().dropItemNaturally(player.getLocation(), itemStack));

        if (reward.givePlayerExp() > 0) {
            player.giveExp(reward.givePlayerExp());
        }

        player.sendMessage(ChatColor.GOLD + config.displayName() + " Lv." + reward.level() + " 보상을 받았습니다.");
    }

    private List<ItemStack> itemStacks(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                itemStack(gson.fromJson(child, SkillConfig.ItemStackConfig.class)).ifPresent(result::add);
            }
            return result;
        }

        itemStack(gson.fromJson(element, SkillConfig.ItemStackConfig.class)).ifPresent(result::add);
        return result;
    }

    private Optional<ItemStack> itemStack(SkillConfig.ItemStackConfig itemConfig) {
        if (itemConfig == null || itemConfig.item() == null || itemConfig.item().isBlank()) {
            return Optional.empty();
        }

        Material material = Material.matchMaterial(itemConfig.item());
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("Unknown reward item material: " + itemConfig.item());
            return Optional.empty();
        }

        ItemStack itemStack = new ItemStack(material, Math.max(1, itemConfig.amount()));
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            if (itemConfig.name() != null && !itemConfig.name().isBlank()) {
                itemMeta.setDisplayName(color(itemConfig.name()));
            }
            if (!itemConfig.lore().isEmpty()) {
                itemMeta.setLore(itemConfig.lore().stream().map(this::color).toList());
            }
            if (itemConfig.nbt().stream().anyMatch(value -> value.equalsIgnoreCase("untradeable"))) {
                itemMeta.getPersistentDataContainer().set(untradeableKey, PersistentDataType.BYTE, (byte) 1);
            }
            for (SkillConfig.EnchantConfig enchantConfig : itemConfig.enchants()) {
                Enchantment enchantment = enchantment(enchantConfig.enchant());
                if (enchantment != null) {
                    itemMeta.addEnchant(enchantment, enchantConfig.level(), true);
                }
            }
            itemStack.setItemMeta(itemMeta);
        }
        return Optional.of(itemStack);
    }

    private Enchantment enchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Enchantment byName = Enchantment.getByName(name.toUpperCase(Locale.ROOT));
        if (byName != null) {
            return byName;
        }
        String key = name.toLowerCase(Locale.ROOT);
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    private void enqueueSave(PlayerSkillState state) {
        state.saveChain(state.saveChain()
                .exceptionally(throwable -> null)
                .thenCompose(unused -> levelDatabase.saveLevel(state.uuid(), state.skill(), state.level(), state.exp()))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to save level progress.", throwable);
                    return null;
                }));
    }

    private double requiredExp(int level) {
        return 100.0D + (Math.max(0, level) * 50.0D);
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private String normalize(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SkillKey(UUID uuid, String skill) {
    }

    private record ComboKey(UUID uuid, String skill, String target) {
    }

    private record ComboState(int count, long lastUpdated) {
    }

    private static final class PlayerSkillState {
        private final UUID uuid;
        private final String skill;
        private int level;
        private double exp;
        private CompletableFuture<Void> saveChain = CompletableFuture.completedFuture(null);

        private PlayerSkillState(UUID uuid, String skill, int level, double exp) {
            this.uuid = uuid;
            this.skill = skill;
            this.level = level;
            this.exp = exp;
        }

        private UUID uuid() {
            return uuid;
        }

        private String skill() {
            return skill;
        }

        private int level() {
            return level;
        }

        private double exp() {
            return exp;
        }

        private void update(int level, double exp) {
            this.level = level;
            this.exp = exp;
        }

        private CompletableFuture<Void> saveChain() {
            return saveChain;
        }

        private void saveChain(CompletableFuture<Void> saveChain) {
            this.saveChain = saveChain;
        }
    }
}
