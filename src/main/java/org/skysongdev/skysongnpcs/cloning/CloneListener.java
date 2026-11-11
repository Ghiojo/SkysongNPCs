package org.skysongdev.skysongnpcs.cloning;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;

/**
 * Copies the sheet as soon as a managed NPC's copy has its record. LOWEST, so the copy's
 * sheet is complete for every other listener of {@link NpcCloneEvent}.
 */
public final class CloneListener implements Listener {
    private final NpcCloning cloning;

    public CloneListener(NpcCloning cloning) {
        this.cloning = cloning;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClone(NpcCloneEvent event) {
        cloning.copied(event.getOriginal().id(), event.getCopy().id());
    }
}
