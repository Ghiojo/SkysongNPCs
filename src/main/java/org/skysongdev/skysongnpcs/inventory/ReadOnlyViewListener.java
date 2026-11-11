package org.skysongdev.skysongnpcs.inventory;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Keeps read-only copies of NPC inventories read-only. */
public final class ReadOnlyViewListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        // Every click while a copy is open is cancelled, shift-clicks and number keys from the
        // player's own inventory included, so nothing moves in or out of it.
        if (event.getInventory().getHolder() instanceof ReadOnlyView) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ReadOnlyView) {
            event.setCancelled(true);
        }
    }
}
