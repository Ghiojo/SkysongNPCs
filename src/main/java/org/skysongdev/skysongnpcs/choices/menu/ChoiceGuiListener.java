package org.skysongdev.skysongnpcs.choices.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps choice pages read-only and passes clicks on them to their buttons. */
public final class ChoiceGuiListener implements Listener {
    private final Logger logger;

    public ChoiceGuiListener(Logger logger) {
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChoiceGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getInventory()) {
            try {
                gui.click(event.getSlot(), event.getClick());
            } catch (RuntimeException failed) {
                logger.log(Level.WARNING, "A choice page failed on a click", failed);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChoiceGui) {
            event.setCancelled(true);
        }
    }
}
