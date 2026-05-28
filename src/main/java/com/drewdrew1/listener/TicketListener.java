package com.drewdrew1.listener;

import com.drewdrew1.guild.GuildService;
import com.drewdrew1.storage.StorageManager;
import com.drewdrew1.ticket.TicketService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class TicketListener implements Listener {
    private final StorageManager storageManager;
    private final GuildService guildService;
    private final TicketService ticketService;

    public TicketListener(StorageManager storageManager, GuildService guildService, TicketService ticketService) {
        this.storageManager = storageManager;
        this.guildService = guildService;
        this.ticketService = ticketService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND && ticketService.isInviteTicket(event.getPlayer().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            guildService.startInvitePrompt(event.getPlayer());
            return;
        }

        ItemStack item = event.getItem();
        if (ticketService.isExpansionTicket(item)) {
            event.setCancelled(true);
            storageManager.expandPersonal(event.getPlayer(), () -> ticketService.consumeExpansionTicket(event.getPlayer()));
        }
    }
}
