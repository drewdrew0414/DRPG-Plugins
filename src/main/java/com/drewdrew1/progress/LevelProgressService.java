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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class LevelProgressService {
    private static final long COMBO_TIMEOUT_MILLIS = 10_000L;
    private static final long SAVE_INTERVAL_TICKS = 100L;
    private static final long EXP_BOSS_BAR_DURATION_TICKS = 60L;

    private final Main plugin;
    private final LevelDatabase levelDatabase;
    private final SkillConfigCache skillConfigCache;
    private final Gson gson = new Gson();
    private final NamespacedKey untradeableKey;
    private final Map<SkillKey, CompletableFuture<PlayerSkillState>> states = new ConcurrentHashMap<>();
    private final Map<ComboKey, ComboState> combos = new ConcurrentHashMap<>();
    private final Map<UUID, ExpBossBar> expBossBars = new ConcurrentHashMap<>();
    private final java.util.Set<RewardKey> rewardsInFlight = ConcurrentHashMap.newKeySet();
    private final BukkitTask saveTask;

    public LevelProgressService(Main plugin, LevelDatabase levelDatabase, SkillConfigCache skillConfigCache) {
        this.plugin = plugin;
        this.levelDatabase = levelDatabase;
        this.skillConfigCache = skillConfigCache;
        this.untradeableKey = new NamespacedKey(plugin, "untradeable");
        this.saveTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::flushDirtyStates,
                SAVE_INTERVAL_TICKS,
                SAVE_INTERVAL_TICKS
        );
    }

    public void addExp(Player player, SkillConfig config, SkillConfig.ExpRule rule, String comboTarget) {
        if (config.name() == null || config.name().isBlank()) {
            return;
        }

        SkillKey key = new SkillKey(player.getUniqueId(), normalize(config.name()));
        CompletableFuture<PlayerSkillState> stateFuture = states.computeIfAbsent(key, unused -> loadState(key, config));
        stateFuture.whenComplete((state, throwable) -> {
            if (throwable != null) {
                states.remove(key, stateFuture);
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

    public void addRawExp(Player player, String skillName, double amount) {
        if (player == null || skillName == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return;
        }

        skillConfigCache.skillConfig(skillName).ifPresent(config -> addRawExp(player, config, amount));
    }

    public void addRawExp(Player player, SkillConfig config, double amount) {
        if (config.name() == null || config.name().isBlank() || !Double.isFinite(amount) || amount <= 0.0D) {
            return;
        }

        SkillKey key = new SkillKey(player.getUniqueId(), normalize(config.name()));
        CompletableFuture<PlayerSkillState> stateFuture = states.computeIfAbsent(key, unused -> loadState(key, config));
        stateFuture.whenComplete((state, throwable) -> {
            if (throwable != null) {
                states.remove(key, stateFuture);
                plugin.getLogger().log(Level.WARNING, "Failed to load level data for " + player.getName(), throwable);
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = plugin.getServer().getPlayer(player.getUniqueId());
                if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                    return;
                }
                applyRawExp(onlinePlayer, config, amount, state);
            });
        });
    }

    public Optional<LevelSnapshot> cachedSnapshot(UUID uuid, String skillName) {
        if (uuid == null || skillName == null) {
            return Optional.empty();
        }

        Optional<SkillConfig> config = skillConfigCache.skillConfig(skillName);
        if (config.isEmpty()) {
            return Optional.empty();
        }

        SkillConfig skillConfig = config.get();
        SkillKey key = new SkillKey(uuid, normalize(skillConfig.name()));
        CompletableFuture<PlayerSkillState> stateFuture = states.computeIfAbsent(key, unused -> loadState(key, skillConfig));
        if (!stateFuture.isDone()) {
            return Optional.empty();
        }
        if (stateFuture.isCompletedExceptionally() || stateFuture.isCancelled()) {
            states.remove(key, stateFuture);
            return Optional.empty();
        }

        PlayerSkillState state = stateFuture.getNow(null);
        return state == null ? Optional.empty() : Optional.of(snapshot(skillConfig, state));
    }

    public LevelSnapshot defaultSnapshot(SkillConfig config) {
        int level = clamp(config.startLevel(), config.startLevel(), config.maxLevel());
        double requiredExp = level >= config.maxLevel() ? 0.0D : requiredExp(config, level);
        return new LevelSnapshot(
                config.name(),
                displayName(config),
                level,
                0.0D,
                requiredExp,
                config.maxLevel()
        );
    }

    public double requiredExpForLevel(int level) {
        return Math.max(1.0D, 100.0D * (Math.max(0, level) + 1.0D));
    }

    public void deliverEarnedRewards(Player player) {
        for (SkillConfig config : skillConfigCache.skillConfigs()) {
            if (config.name() == null || config.name().isBlank()) {
                continue;
            }

            SkillKey key = new SkillKey(player.getUniqueId(), normalize(config.name()));
            CompletableFuture<PlayerSkillState> stateFuture = states.computeIfAbsent(key, unused -> loadState(key, config));
            stateFuture.whenComplete((state, throwable) -> {
                if (throwable != null) {
                    states.remove(key, stateFuture);
                    plugin.getLogger().log(Level.WARNING, "Failed to load rewards for " + player.getName(), throwable);
                    return;
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = plugin.getServer().getPlayer(player.getUniqueId());
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        giveReachedRewards(onlinePlayer, config, config.startLevel() - 1, state.level());
                    }
                });
            });
        }
    }

    public void unloadPlayer(UUID uuid) {
        combos.keySet().removeIf(key -> key.uuid().equals(uuid));
        removeExpBossBar(uuid);

        List<Map.Entry<SkillKey, CompletableFuture<PlayerSkillState>>> playerStates = states.entrySet().stream()
                .filter(entry -> entry.getKey().uuid().equals(uuid))
                .toList();

        for (Map.Entry<SkillKey, CompletableFuture<PlayerSkillState>> entry : playerStates) {
            entry.getValue()
                    .thenCompose(this::saveState)
                    .whenComplete((unused, throwable) -> {
                        if (throwable != null) {
                            plugin.getLogger().log(Level.WARNING, "Failed to save player level progress on quit.", throwable);
                        }
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) {
                            states.remove(entry.getKey(), entry.getValue());
                        }
                    });
        }
    }

    public void clearMemoryCache() {
        states.clear();
        combos.clear();
        rewardsInFlight.clear();
        removeAllExpBossBars();
    }

    public CompletableFuture<Void> shutdown() {
        saveTask.cancel();
        removeAllExpBossBars();
        return flush();
    }

    public CompletableFuture<Void> flush() {
        CompletableFuture<?>[] saves = states.values().stream()
                .filter(CompletableFuture::isDone)
                .map(future -> future.thenCompose(this::saveState))
                .toArray(CompletableFuture[]::new);

        if (saves.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(saves);
    }

    private void flushDirtyStates() {
        cleanupCombos();
        for (CompletableFuture<PlayerSkillState> future : states.values()) {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                future.thenAccept(this::saveState);
            }
        }
    }

    private CompletableFuture<PlayerSkillState> loadState(SkillKey key, SkillConfig config) {
        return levelDatabase.loadLevel(key.uuid(), config.name())
                .thenApply(data -> data
                        .map(levelData -> stateFromData(key, config, levelData.level(), levelData.exp()))
                        .orElseGet(() -> new PlayerSkillState(
                                key.uuid(),
                                config.name(),
                                config.startLevel(),
                                0.0D
                        )));
    }

    private PlayerSkillState stateFromData(SkillKey key, SkillConfig config, int storedLevel, double storedExp) {
        int clampedLevel = clamp(storedLevel, config.startLevel(), config.maxLevel());
        double exp = Math.max(0.0D, storedExp);
        while (clampedLevel < config.maxLevel()) {
            double requiredExp = requiredExp(config, clampedLevel);
            if (exp < requiredExp) {
                break;
            }
            exp -= requiredExp;
            clampedLevel++;
        }
        if (clampedLevel >= config.maxLevel()) {
            clampedLevel = config.maxLevel();
            exp = 0.0D;
        }

        PlayerSkillState state = new PlayerSkillState(key.uuid(), config.name(), clampedLevel, exp);
        state.dirty(clampedLevel != storedLevel || Double.compare(exp, storedExp) != 0);
        return state;
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

        applyRawExp(player, config, gainedExp, state);
    }

    private void applyRawExp(Player player, SkillConfig config, double gainedExp, PlayerSkillState state) {
        if (state.level() >= config.maxLevel() || !Double.isFinite(gainedExp) || gainedExp <= 0.0D) {
            return;
        }

        int beforeLevel = state.level();
        double exp = state.exp() + gainedExp;
        int level = state.level();
        while (level < config.maxLevel()) {
            double requiredExp = requiredExp(config, level);
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
        state.dirty(true);
        showExpBossBar(player, config, gainedExp, level, exp);

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

            RewardKey rewardKey = new RewardKey(player.getUniqueId(), normalize(config.name()), reward.level());
            if (!rewardsInFlight.add(rewardKey)) {
                continue;
            }

            levelDatabase.hasRewardClaimed(player.getUniqueId(), config.name(), reward.level())
                    .thenAccept(claimed -> {
                        if (claimed) {
                            rewardsInFlight.remove(rewardKey);
                            return;
                        }

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            Player onlinePlayer = plugin.getServer().getPlayer(player.getUniqueId());
                            if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                                rewardsInFlight.remove(rewardKey);
                                return;
                            }

                            giveReward(onlinePlayer, config, reward);
                            levelDatabase.markRewardClaimed(onlinePlayer.getUniqueId(), config.name(), reward.level())
                                    .thenRun(() -> rewardsInFlight.remove(rewardKey))
                                    .exceptionally(throwable -> {
                                        plugin.getLogger().log(
                                                Level.WARNING,
                                                "Reward was given but failed to mark as claimed.",
                                                throwable
                                        );
                                        rewardsInFlight.remove(rewardKey);
                                        return null;
                                    });
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.WARNING, "Failed to claim level reward.", throwable);
                        rewardsInFlight.remove(rewardKey);
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

        String key = name.toLowerCase(Locale.ROOT);
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    private CompletableFuture<Void> saveState(PlayerSkillState state) {
        synchronized (state) {
            if (!state.dirty()) {
                return state.saveChain();
            }

            int level = state.level();
            double exp = state.exp();
            state.dirty(false);
            CompletableFuture<Void> save = state.saveChain()
                    .exceptionally(throwable -> null)
                    .thenCompose(unused -> levelDatabase.saveLevel(state.uuid(), state.skill(), level, exp))
                    .exceptionally(throwable -> {
                        state.dirty(true);
                        plugin.getLogger().log(Level.WARNING, "Failed to save level progress.", throwable);
                        return null;
                    });
            state.saveChain(save);
            return save;
        }
    }

    private void showExpBossBar(Player player, SkillConfig config, double gainedExp, int level, double exp) {
        double requiredExp = level >= config.maxLevel() ? 0.0D : requiredExp(config, level);
        double progress = level >= config.maxLevel()
                ? 1.0D
                : requiredExp <= 0.0D ? 0.0D : clamp(exp / requiredExp, 0.0D, 1.0D);
        String title = ChatColor.GREEN + "+" + formatNumber(gainedExp) + " EXP "
                + ChatColor.WHITE + displayName(config)
                + ChatColor.GRAY + " | Lv." + level + " "
                + ChatColor.AQUA + formatNumber(exp) + "/" + formatNumber(requiredExp)
                + ChatColor.GRAY + " (" + formatNumber(progress * 100.0D) + "%)";

        UUID uuid = player.getUniqueId();
        removeExpBossBar(uuid);

        BossBar bossBar = plugin.getServer().createBossBar(
                title,
                bossBarColor(config.bossBarColor()),
                BarStyle.SOLID
        );
        bossBar.setProgress(progress);
        bossBar.addPlayer(player);

        BukkitTask removeTask = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> removeExpBossBar(uuid),
                EXP_BOSS_BAR_DURATION_TICKS
        );
        expBossBars.put(uuid, new ExpBossBar(bossBar, removeTask));
    }

    private void removeExpBossBar(UUID uuid) {
        ExpBossBar expBossBar = expBossBars.remove(uuid);
        if (expBossBar != null) {
            expBossBar.close();
        }
    }

    private void removeAllExpBossBars() {
        expBossBars.values().forEach(ExpBossBar::close);
        expBossBars.clear();
    }

    private BarColor bossBarColor(String value) {
        try {
            return BarColor.valueOf(Objects.toString(value, "GREEN").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BarColor.GREEN;
        }
    }

    private void cleanupCombos() {
        long now = System.currentTimeMillis();
        combos.entrySet().removeIf(entry -> now - entry.getValue().lastUpdated() > COMBO_TIMEOUT_MILLIS);
    }

    private LevelSnapshot snapshot(SkillConfig config, PlayerSkillState state) {
        synchronized (state) {
            int level = state.level();
            double exp = state.exp();
            double requiredExp = level >= config.maxLevel() ? 0.0D : requiredExp(config, level);
            return new LevelSnapshot(
                    config.name(),
                    displayName(config),
                    level,
                    exp,
                    requiredExp,
                    config.maxLevel()
            );
        }
    }

    private double requiredExp(SkillConfig config, int level) {
        return Math.max(1.0D, config.requiredExpPerLevel()) * (Math.max(0, level) + 1.0D);
    }

    private String displayName(SkillConfig config) {
        return config.displayName() == null || config.displayName().isBlank()
                ? config.name()
                : ChatColor.stripColor(color(config.displayName()));
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private String formatNumber(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.000001D) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String normalize(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record LevelSnapshot(
            String skill,
            String displayName,
            int level,
            double exp,
            double requiredExp,
            int maxLevel
    ) {
        public double progressPercent() {
            if (level >= maxLevel) {
                return 100.0D;
            }
            if (requiredExp <= 0.0D) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(100.0D, (exp / requiredExp) * 100.0D));
        }
    }

    private record SkillKey(UUID uuid, String skill) {
    }

    private record ComboKey(UUID uuid, String skill, String target) {
    }

    private record ComboState(int count, long lastUpdated) {
    }

    private record RewardKey(UUID uuid, String skill, int level) {
    }

    private record ExpBossBar(BossBar bossBar, BukkitTask removeTask) {
        private void close() {
            removeTask.cancel();
            bossBar.removeAll();
        }
    }

    private static final class PlayerSkillState {
        private final UUID uuid;
        private final String skill;
        private int level;
        private double exp;
        private boolean dirty;
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

        private boolean dirty() {
            return dirty;
        }

        private void dirty(boolean dirty) {
            this.dirty = dirty;
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
