package com.drewdrew1.commodity;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class CommodityService {
    private final JavaPlugin plugin;
    private final CommodityConfig config;
    private final CommodityRepository repository;
    private final AtomicReference<CommoditySettings> settings;
    private final ConcurrentMap<UUID, PlayerCommodityData> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> loadingPlayers = new ConcurrentHashMap<>();

    public CommodityService(
            JavaPlugin plugin,
            CommodityConfig config,
            CommodityRepository repository,
            CommoditySettings settings
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.settings = new AtomicReference<>(settings);
    }

    public CommoditySettings settings() {
        return settings.get();
    }

    public CommodityConfig config() {
        return config;
    }

    public CompletableFuture<CommoditySettings> reload() {
        try {
            CommoditySettings reloaded = config.load();
            settings.set(reloaded);
            return reloadOnlinePlayers(false).thenApply(ignored -> reloaded);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> loadOnlinePlayers(boolean processOffline) {
        return reloadOnlinePlayers(processOffline);
    }

    public CompletableFuture<PlayerCommodityData> loadPlayer(OfflinePlayer player, String fallbackName, boolean processOffline) {
        UUID playerId = player.getUniqueId();
        String name = safeName(player, fallbackName);
        return repository.loadPlayer(playerId, name, settings(), now(), processOffline)
                .thenApply(data -> {
                    cache.put(playerId, data);
                    loadingPlayers.remove(playerId);
                    return data;
                })
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        loadingPlayers.remove(playerId);
                    }
                });
    }

    public CompletableFuture<CommodityChangeResult> change(
            OfflinePlayer player,
            String fallbackName,
            CommodityDefinition definition,
            CommodityOperation operation,
            long operand
    ) {
        UUID playerId = player.getUniqueId();
        String name = safeName(player, fallbackName);
        return repository.changeAmount(playerId, name, settings(), definition, operation, operand, now())
                .thenApply(result -> {
                    cache.put(playerId, new PlayerCommodityData(playerId, result.playerName(), result.amounts()));
                    return result;
                });
    }

    public CompletableFuture<PlayerCommodityData> resetAll(OfflinePlayer player, String fallbackName) {
        UUID playerId = player.getUniqueId();
        String name = safeName(player, fallbackName);
        return repository.resetAll(playerId, name, settings(), now())
                .thenApply(data -> {
                    cache.put(playerId, data);
                    return data;
                });
    }

    public CompletableFuture<PlayerCommodityData> snapshot(OfflinePlayer player, String fallbackName) {
        PlayerCommodityData cached = cache.get(player.getUniqueId());
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return loadPlayer(player, fallbackName, true);
    }

    public long placeholderAmount(OfflinePlayer player, CommodityDefinition definition) {
        if (player == null) {
            return definition.defaultValue();
        }
        PlayerCommodityData cached = cache.get(player.getUniqueId());
        if (cached != null) {
            return cached.amount(definition.key(), definition.defaultValue());
        }
        requestBackgroundLoad(player, null, true);
        return definition.defaultValue();
    }

    public void markOffline(Player player) {
        cache.remove(player.getUniqueId());
        repository.markOffline(player.getUniqueId(), player.getName(), now())
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to save commodity offline timestamp for " + player.getName(), unwrap(error));
                    return null;
                });
    }

    public void shutdown() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        long timestamp = now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            saves.add(repository.markOffline(player.getUniqueId(), player.getName(), timestamp)
                    .exceptionally(error -> {
                        plugin.getLogger().log(Level.WARNING, "Failed to save commodity offline timestamp for " + player.getName(), unwrap(error));
                        return null;
                    }));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
        try {
            all.get(3L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Timed out while saving commodity offline timestamps.", exception);
        }
        cache.clear();
        loadingPlayers.clear();
    }

    public void runSync(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public void requestBackgroundLoad(OfflinePlayer player, String fallbackName, boolean processOffline) {
        UUID playerId = player.getUniqueId();
        if (loadingPlayers.putIfAbsent(playerId, Boolean.TRUE) != null) {
            return;
        }
        loadPlayer(player, fallbackName, processOffline)
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to load commodity data for " + safeName(player, fallbackName), unwrap(error));
                    return null;
                });
    }

    private CompletableFuture<Void> reloadOnlinePlayers(boolean processOffline) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            futures.add(loadPlayer(player, player.getName(), processOffline));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private static String safeName(OfflinePlayer player, String fallbackName) {
        String name = player.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName;
        }
        return player.getUniqueId().toString();
    }

    public static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
