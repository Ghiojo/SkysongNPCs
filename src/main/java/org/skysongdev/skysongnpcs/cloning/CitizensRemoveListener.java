package org.skysongdev.skysongnpcs.cloning;

import net.citizensnpcs.api.event.NPCRemoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Deletes a removed NPC's sheet. It listens to Citizens rather than to the plugin's own
 * delete event so that an NPC staff released (which keeps its sheet) loses it too when it is
 * removed later. Citizens fires {@link NPCRemoveEvent} only when an NPC is destroyed for good.
 */
public final class CitizensRemoveListener implements Listener {
    private final NpcCloning cloning;

    public CitizensRemoveListener(NpcCloning cloning) {
        this.cloning = cloning;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(NPCRemoveEvent event) {
        cloning.removed(event.getNPC().getUniqueId());
    }
}
