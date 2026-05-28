package com.drewdrew1.ticket;

import com.drewdrew1.Main;
import com.drewdrew1.config.StorageGuildConfig;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class TicketService {
    private static final String INVITE_TICKET = "guild_invite";
    private static final String EXPANSION_TICKET = "storage_expansion";

    private final NamespacedKey ticketKey;
    private final StorageGuildConfig config;

    public TicketService(Main plugin, StorageGuildConfig config) {
        this.ticketKey = new NamespacedKey(plugin, "ticket_type");
        this.config = config;
    }

    public ItemStack createInviteTicket(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("길드 초대권"));
        meta.lore(List.of(
                Component.text("왼손에 들고 우클릭한 뒤"),
                Component.text("채팅에 온라인 플레이어 이름을 입력하세요.")
        ));
        meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.STRING, INVITE_TICKET);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createExpansionTicket(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("창고 확장권"));
        meta.lore(List.of(
                Component.text("우클릭하면 개인 창고가 " + config.slotsPerExpansion() + "칸 확장됩니다."),
                Component.text("/storage expand <길드> 로 길드 창고를 확장할 수 있습니다.")
        ));
        meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.STRING, EXPANSION_TICKET);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isInviteTicket(ItemStack item) {
        return hasTicketType(item, INVITE_TICKET);
    }

    public boolean isExpansionTicket(ItemStack item) {
        return hasTicketType(item, EXPANSION_TICKET);
    }

    public boolean hasInviteTicketInOffhand(Player player) {
        return isInviteTicket(player.getInventory().getItemInOffHand());
    }

    public boolean consumeInviteTicketFromOffhand(Player player) {
        return consumeFromSlot(player, EquipmentSlot.OFF_HAND, this::isInviteTicket);
    }

    public boolean consumeExpansionTicket(Player player) {
        if (consumeFromSlot(player, EquipmentSlot.HAND, this::isExpansionTicket)) {
            return true;
        }
        return consumeFromSlot(player, EquipmentSlot.OFF_HAND, this::isExpansionTicket);
    }

    private boolean hasTicketType(ItemStack item, String ticketType) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String storedType = item.getItemMeta().getPersistentDataContainer().get(ticketKey, PersistentDataType.STRING);
        return ticketType.equals(storedType);
    }

    private boolean consumeFromSlot(Player player, EquipmentSlot slot, Predicate<ItemStack> matcher) {
        PlayerInventory inventory = player.getInventory();
        ItemStack item = slot == EquipmentSlot.HAND ? inventory.getItemInMainHand() : inventory.getItemInOffHand();
        if (!matcher.test(item)) {
            return false;
        }
        int amount = item.getAmount();
        if (amount <= 1) {
            if (slot == EquipmentSlot.HAND) {
                inventory.setItemInMainHand(null);
            } else {
                inventory.setItemInOffHand(null);
            }
        } else {
            item.setAmount(amount - 1);
        }
        return true;
    }
}
