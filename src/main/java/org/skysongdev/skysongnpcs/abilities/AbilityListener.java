package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;

/**
 * Keeps abilities in step with their NPCs: they go when its record is deleted and come along
 * when it is copied. MONITOR, so the copy's stat sheet is already copied (M13 does it at LOWEST).
 */
public final class AbilityListener implements Listener {
    private final AbilityService abilities;

    public AbilityListener(AbilityService abilities) {
        this.abilities = abilities;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDelete(NpcDeleteEvent event) {
        abilities.deleted(event.getNpc().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClone(NpcCloneEvent event) {
        abilities.copied(event.getOriginal().id(), event.getCopy().id());
    }
}
