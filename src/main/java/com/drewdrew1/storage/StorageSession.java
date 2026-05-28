package com.drewdrew1.storage;

import com.drewdrew1.config.StorageGuildConfig;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StorageSession {
    private final StorageGuildConfig config;
    private final String storageKey;
    private final String title;
    private final Map<Integer, ItemStack> items = new HashMap<>();
    private final Map<Integer, Inventory> inventories = new HashMap<>();
    private CompletableFuture<Void> saveChain = CompletableFuture.completedFuture(null);
    private int unlockedSlots;

    StorageSession(StorageGuildConfig config, String storageKey, String title, int unlockedSlots, Map<Integer, ItemStack> loadedItems) {
        this.config = config;
        this.storageKey = storageKey;
        this.title = title;
        this.unlockedSlots = unlockedSlots;
        loadedItems.forEach((slot, item) -> {
            if (slot >= 0 && item != null && !item.getType().isAir()) {
                items.put(slot, item.clone());
            }
        });
    }

    public String storageKey() {
        return storageKey;
    }

    public int unlockedSlots() {
        return unlockedSlots;
    }

    public void setUnlockedSlots(int unlockedSlots) {
        this.unlockedSlots = unlockedSlots;
        inventories.values().forEach(this::render);
    }

    public int pageCount() {
        return Math.max(1, (int) Math.ceil(unlockedSlots / (double) config.pageItemSlots()));
    }

    public Inventory inventory(int requestedPage) {
        int page = clampPage(requestedPage);
        return inventories.computeIfAbsent(page, this::createInventory);
    }

    public int clampPage(int page) {
        return Math.max(0, Math.min(page, pageCount() - 1));
    }

    public boolean isStorageSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < config.pageItemSlots();
    }

    public boolean isControlSlot(int rawSlot) {
        return rawSlot >= config.pageItemSlots() && rawSlot < config.guiSize();
    }

    public boolean isUnlocked(int page, int rawSlot) {
        return isStorageSlot(rawSlot) && logicalSlot(page, rawSlot) < unlockedSlots;
    }

    public int logicalSlot(int page, int rawSlot) {
        return page * config.pageItemSlots() + rawSlot;
    }

    public Map<Integer, ItemStack> snapshot() {
        inventories.forEach((page, inventory) -> applyInventory(page, inventory));
        Map<Integer, ItemStack> snapshot = new LinkedHashMap<>();
        items.forEach((slot, item) -> {
            if (slot >= 0 && slot < unlockedSlots && item != null && !item.getType().isAir()) {
                snapshot.put(slot, item.clone());
            }
        });
        return snapshot;
    }

    public ItemStack addItem(ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return null;
        }
        inventories.forEach((page, inventory) -> applyInventory(page, inventory));
        ItemStack remaining = source.clone();

        for (int slot = 0; slot < unlockedSlots && remaining.getAmount() > 0; slot++) {
            ItemStack existing = items.get(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(remaining)) {
                continue;
            }
            int movable = Math.min(remaining.getAmount(), existing.getMaxStackSize() - existing.getAmount());
            if (movable <= 0) {
                continue;
            }
            existing.setAmount(existing.getAmount() + movable);
            remaining.setAmount(remaining.getAmount() - movable);
        }

        for (int slot = 0; slot < unlockedSlots && remaining.getAmount() > 0; slot++) {
            ItemStack existing = items.get(slot);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int movable = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(movable);
            items.put(slot, placed);
            remaining.setAmount(remaining.getAmount() - movable);
        }

        inventories.values().forEach(this::render);
        return remaining.getAmount() <= 0 ? null : remaining;
    }

    public synchronized void chainSave(Supplier<CompletableFuture<Void>> saveFuture) {
        saveChain = saveChain.handle((ignored, throwable) -> null).thenCompose(ignored -> saveFuture.get());
    }

    public synchronized CompletableFuture<Void> currentSaveChain() {
        return saveChain;
    }

    private Inventory createInventory(int page) {
        StorageHolder holder = new StorageHolder(this, page);
        Inventory inventory = Bukkit.createInventory(holder, config.guiSize(), title + " " + (page + 1) + "/" + pageCount());
        render(inventory);
        return inventory;
    }

    private void render(Inventory inventory) {
        if (!(inventory.getHolder() instanceof StorageHolder holder)) {
            return;
        }
        int page = holder.page();
        for (int rawSlot = 0; rawSlot < config.pageItemSlots(); rawSlot++) {
            int logicalSlot = logicalSlot(page, rawSlot);
            if (logicalSlot >= unlockedSlots) {
                inventory.setItem(rawSlot, lockedItem());
                continue;
            }
            inventory.setItem(rawSlot, cloneOrNull(items.get(logicalSlot)));
        }
        for (int rawSlot = config.pageItemSlots(); rawSlot < config.guiSize(); rawSlot++) {
            inventory.setItem(rawSlot, fillerItem());
        }
        inventory.setItem(config.pageItemSlots(), page > 0 ? previousItem() : fillerItem());
        inventory.setItem(config.guiSize() - 5, infoItem(page));
        inventory.setItem(config.guiSize() - 1, page + 1 < pageCount() ? nextItem() : fillerItem());
    }

    private void applyInventory(int page, Inventory inventory) {
        for (int rawSlot = 0; rawSlot < config.pageItemSlots(); rawSlot++) {
            int logicalSlot = logicalSlot(page, rawSlot);
            if (logicalSlot >= unlockedSlots) {
                items.remove(logicalSlot);
                continue;
            }
            ItemStack item = inventory.getItem(rawSlot);
            if (item == null || item.getType().isAir()) {
                items.remove(logicalSlot);
            } else {
                items.put(logicalSlot, item.clone());
            }
        }
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private ItemStack lockedItem() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("잠긴 칸"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack fillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack previousItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("이전 페이지"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack nextItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("다음 페이지"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem(int page) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("페이지 " + (page + 1) + "/" + pageCount() + " · " + unlockedSlots + "칸"));
        item.setItemMeta(meta);
        return item;
    }
}
