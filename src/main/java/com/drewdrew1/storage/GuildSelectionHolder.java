package com.drewdrew1.storage;

import com.drewdrew1.model.Guild;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuildSelectionHolder implements InventoryHolder {
    private final Map<Integer, Guild> guildsBySlot = new HashMap<>();
    private Inventory inventory;

    public GuildSelectionHolder(List<Guild> guilds) {
        for (int index = 0; index < guilds.size() && index < 54; index++) {
            guildsBySlot.put(index, guilds.get(index));
        }
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    public Guild guildAt(int slot) {
        return guildsBySlot.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
