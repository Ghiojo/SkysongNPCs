package org.skysongdev.skysongnpcs.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.server.PluginDisableEvent;

/**
 * Keeps NPC menus read-only, passes clicks on their buttons to the menu, and drops a plugin's
 * buttons when it disables.
 */
public final class MenuListener implements Listener {
    private final NpcMenuService menu;

    public MenuListener(NpcMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof NpcMenuHolder holder)) {
            return;
        }
        // Every click while a menu is open is cancelled, including shift-clicks from the
        // player's own inventory, so nothing moves in or out of it.
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getInventory()) {
            menu.click(holder, event.getSlot(), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof NpcMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        menu.unregisterAll(event.getPlugin());
    }
}
