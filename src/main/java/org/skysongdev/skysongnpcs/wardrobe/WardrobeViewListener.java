package org.skysongdev.skysongnpcs.wardrobe;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Keeps the wardrobe view read-only, and goes back to the NPC menu when it closes. */
public final class WardrobeViewListener implements Listener {
    private final NpcWardrobes wardrobes;

    public WardrobeViewListener(NpcWardrobes wardrobes) {
        this.wardrobes = wardrobes;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        // Every click, shift-clicks from the player's own inventory included, so nothing moves in or out.
        if (event.getInventory().getHolder() instanceof WardrobeViewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WardrobeViewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof WardrobeViewHolder holder && holder.backToMenu()
                && event.getPlayer() instanceof Player player) {
            wardrobes.backToMenu(player, holder.npc());
        }
    }
}
