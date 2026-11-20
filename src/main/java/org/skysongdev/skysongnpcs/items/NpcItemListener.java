package org.skysongdev.skysongnpcs.items;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;

/**
 * A deleted NPC's item data goes with it, so its outstanding items stop working; a copied
 * NPC's copy gets the same pickup item.
 */
public final class NpcItemListener implements Listener {
    private final NpcPickup pickup;

    public NpcItemListener(NpcPickup pickup) {
        this.pickup = pickup;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDelete(NpcDeleteEvent event) {
        pickup.deleted(event.getNpc().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClone(NpcCloneEvent event) {
        pickup.items().copied(event.getOriginal().id(), event.getCopy().id());
    }
}
