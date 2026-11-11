package org.skysongdev.skysongnpcs.access;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;

/**
 * Keeps grants in step with their NPCs: they go when its record is deleted and come along
 * when it is copied. Also forgets a plugin's group providers when it disables.
 */
public final class AccessListener implements Listener {
    private final AccessService access;

    public AccessListener(AccessService access) {
        this.access = access;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDelete(NpcDeleteEvent event) {
        access.grants().deleteAll(event.getNpc().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClone(NpcCloneEvent event) {
        access.grants().copy(event.getOriginal().id(), event.getCopy().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        access.unregisterAll(event.getPlugin());
    }
}
