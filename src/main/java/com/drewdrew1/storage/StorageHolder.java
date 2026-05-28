package com.drewdrew1.storage;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class StorageHolder implements InventoryHolder {
    private final StorageSession session;
    private final int page;

    StorageHolder(StorageSession session, int page) {
        this.session = session;
        this.page = page;
    }

    public StorageSession session() {
        return session;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return session.inventory(page);
    }
}
