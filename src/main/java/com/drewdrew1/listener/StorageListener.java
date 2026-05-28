package com.drewdrew1.listener;

import com.drewdrew1.model.Guild;
import com.drewdrew1.storage.GuildSelectionHolder;
import com.drewdrew1.storage.StorageHolder;
import com.drewdrew1.storage.StorageManager;
import com.drewdrew1.storage.StorageSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class StorageListener implements Listener {
    private final StorageManager storageManager;

    public StorageListener(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof GuildSelectionHolder holder) {
            event.setCancelled(true);
            Guild guild = holder.guildAt(event.getRawSlot());
            if (guild != null) {
                storageManager.handleGuildSelection(player, guild);
            }
            return;
        }

        if (!(top.getHolder() instanceof StorageHolder holder)) {
            return;
        }

        StorageSession session = holder.session();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            if (session.isControlSlot(rawSlot)) {
                event.setCancelled(true);
                storageManager.handlePageControl(player, holder, rawSlot);
                return;
            }
            if (session.isStorageSlot(rawSlot) && !session.isUnlocked(holder.page(), rawSlot)) {
                event.setCancelled(true);
                return;
            }
            storageManager.scheduleSave(session);
            return;
        }

        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                event.setCancelled(true);
                event.setCurrentItem(session.addItem(current));
            }
            storageManager.scheduleSave(session);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof StorageHolder holder)) {
            return;
        }
        StorageSession session = holder.session();
        boolean touchesTop = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= top.getSize()) {
                continue;
            }
            touchesTop = true;
            if (session.isControlSlot(rawSlot) || !session.isUnlocked(holder.page(), rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        if (touchesTop) {
            storageManager.scheduleSave(session);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof StorageHolder holder) {
            storageManager.scheduleSave(holder.session());
        }
    }
}
