package com.drewdrew1.storage;

import com.drewdrew1.Main;
import com.drewdrew1.config.StorageGuildConfig;
import com.drewdrew1.db.StorageRepository;
import com.drewdrew1.guild.GuildService;
import com.drewdrew1.model.Guild;
import com.drewdrew1.model.GuildMember;
import com.drewdrew1.model.StorageRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StorageManager {
    private final Main plugin;
    private final StorageRepository repository;
    private final GuildService guildService;
    private final StorageGuildConfig config;
    private final Map<String, StorageSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public StorageManager(Main plugin, StorageRepository repository, GuildService guildService, StorageGuildConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.guildService = guildService;
        this.config = config;
    }

    public void openPersonal(Player player, int page) {
        String storageKey = StorageId.personal(player.getUniqueId()).databaseKey();
        StorageSession existing = sessions.get(storageKey);
        if (existing != null) {
            player.openInventory(existing.inventory(page));
            return;
        }

        repository.getOrCreatePersonalStorage(player.getUniqueId())
                .thenCompose(record -> repository.loadItems(record.storageKey()).thenApply(items -> new LoadedStorage(record, items)))
                .whenComplete((loaded, throwable) -> runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (throwable != null) {
                        player.sendMessage("창고를 불러오지 못했습니다: " + rootMessage(throwable));
                        return;
                    }
                    StorageSession session = sessions.computeIfAbsent(loaded.record().storageKey(), ignored -> new StorageSession(
                            config,
                            loaded.record().storageKey(),
                            "개인 창고",
                            loaded.record().unlockedSlots(),
                            loaded.items()
                    ));
                    player.openInventory(session.inventory(page));
                }));
    }

    public void openGuild(Player player, Guild guild, int page) {
        guildService.member(guild.id(), player.getUniqueId()).whenComplete((member, throwable) -> runSync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (throwable != null || member.isEmpty()) {
                player.sendMessage("해당 길드 창고를 열 권한이 없습니다.");
                return;
            }
            openGuildUnchecked(player, guild, page);
        }));
    }

    public void openGuildUnchecked(Player player, Guild guild, int page) {
        String storageKey = StorageId.guild(guild.id()).databaseKey();
        StorageSession existing = sessions.get(storageKey);
        if (existing != null) {
            player.openInventory(existing.inventory(page));
            return;
        }

        repository.getOrCreateGuildStorage(guild.id())
                .thenCompose(record -> repository.loadItems(record.storageKey()).thenApply(items -> new LoadedStorage(record, items)))
                .whenComplete((loaded, throwable) -> runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (throwable != null) {
                        player.sendMessage("길드 창고를 불러오지 못했습니다: " + rootMessage(throwable));
                        return;
                    }
                    StorageSession session = sessions.computeIfAbsent(loaded.record().storageKey(), ignored -> new StorageSession(
                            config,
                            loaded.record().storageKey(),
                            "길드 창고: " + guild.name(),
                            loaded.record().unlockedSlots(),
                            loaded.items()
                    ));
                    player.openInventory(session.inventory(page));
                }));
    }

    public void openGuildSelector(Player player, List<Guild> guilds) {
        int size = Math.min(54, Math.max(9, ((guilds.size() + 8) / 9) * 9));
        GuildSelectionHolder holder = new GuildSelectionHolder(guilds);
        Inventory inventory = Bukkit.createInventory(holder, size, "가입한 길드");
        holder.attach(inventory);
        for (int slot = 0; slot < guilds.size() && slot < size; slot++) {
            Guild guild = guilds.get(slot);
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(guild.name()));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
        player.openInventory(inventory);
    }

    public void handleGuildSelection(Player player, Guild guild) {
        openGuild(player, guild, 0);
    }

    public void handlePageControl(Player player, StorageHolder holder, int rawSlot) {
        StorageSession session = holder.session();
        int page = holder.page();
        if (rawSlot == config.pageItemSlots() && page > 0) {
            player.openInventory(session.inventory(page - 1));
            return;
        }
        if (rawSlot == config.guiSize() - 1 && page + 1 < session.pageCount()) {
            player.openInventory(session.inventory(page + 1));
        }
    }

    public void scheduleSave(StorageSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Map<Integer, ItemStack> snapshot = session.snapshot();
            session.chainSave(() -> repository.saveItems(session.storageKey(), snapshot)
                    .exceptionally(throwable -> {
                        plugin.getLogger().warning("Failed to save storage " + session.storageKey() + ": " + rootMessage(throwable));
                        return null;
                    }));
        });
    }

    public void expandPersonal(Player player) {
        expandPersonal(player, () -> {
        });
    }

    public void expandPersonal(Player player, Runnable afterSuccess) {
        String storageKey = StorageId.personal(player.getUniqueId()).databaseKey();
        repository.getOrCreatePersonalStorage(player.getUniqueId())
                .thenCompose(record -> repository.expandStorage(storageKey, config.slotsPerExpansion(), config.maxStorageSlots()))
                .whenComplete((record, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        player.sendMessage("창고를 확장하지 못했습니다: " + rootMessage(throwable));
                        return;
                    }
                    afterSuccess.run();
                    updateSession(record);
                    player.sendMessage("개인 창고가 " + record.unlockedSlots() + "칸으로 확장되었습니다.");
                }));
    }

    public void expandGuild(Player player, Guild guild) {
        expandGuild(player, guild, () -> {
        });
    }

    public void expandGuild(Player player, Guild guild, Runnable afterSuccess) {
        guildService.member(guild.id(), player.getUniqueId()).whenComplete((member, memberThrowable) -> runSync(() -> {
            if (memberThrowable != null || member.isEmpty() || !member.get().role().canManageStorage()) {
                player.sendMessage("길드 창고를 확장할 권한이 없습니다.");
                return;
            }
            String storageKey = StorageId.guild(guild.id()).databaseKey();
            repository.getOrCreateGuildStorage(guild.id())
                    .thenCompose(record -> repository.expandStorage(storageKey, config.slotsPerExpansion(), config.maxStorageSlots()))
                    .whenComplete((record, throwable) -> runSync(() -> {
                        if (throwable != null) {
                            player.sendMessage("길드 창고를 확장하지 못했습니다: " + rootMessage(throwable));
                            return;
                        }
                        afterSuccess.run();
                        updateSession(record);
                        player.sendMessage(guild.name() + " 길드 창고가 " + record.unlockedSlots() + "칸으로 확장되었습니다.");
                    }));
        }));
    }

    public void flushAll() {
        for (StorageSession session : sessions.values()) {
            Map<Integer, ItemStack> snapshot = session.snapshot();
            session.chainSave(() -> repository.saveItems(session.storageKey(), snapshot));
        }
        sessions.values().forEach(session -> session.currentSaveChain().join());
    }

    public StorageGuildConfig config() {
        return config;
    }

    private void updateSession(StorageRecord record) {
        StorageSession session = sessions.get(record.storageKey());
        if (session != null) {
            session.setUnlockedSlots(record.unlockedSlots());
        }
    }

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
        while (current.getCause() != null && current instanceof CompletionException) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record LoadedStorage(StorageRecord record, Map<Integer, ItemStack> items) {
    }
}
