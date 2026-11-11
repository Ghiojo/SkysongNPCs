package org.skysongdev.skysongnpcs.picks;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;

/** Proficiency picks follow their NPC: dropped when it is deleted or released, copied with {@code /npc copy}. */
public final class PicksListener implements Listener {
    private final ProficiencyPicks picks;

    public PicksListener(ProficiencyPicks picks) {
        this.picks = picks;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDelete(NpcDeleteEvent event) {
        picks.deleteAll(event.getNpc().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClone(NpcCloneEvent event) {
        picks.copy(event.getOriginal().id(), event.getCopy().id());
    }
}
