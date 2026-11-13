package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;

/**
 * Ability choices follow their NPC: dropped when it is deleted or released, copied with
 * {@code /npc copy}. MONITOR, after the abilities themselves were copied.
 */
public final class AbilityChoiceListener implements Listener {
    private final AbilityChoiceService choices;

    public AbilityChoiceListener(AbilityChoiceService choices) {
        this.choices = choices;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDelete(NpcDeleteEvent event) {
        choices.deleteAll(event.getNpc().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClone(NpcCloneEvent event) {
        choices.copy(event.getOriginal().id(), event.getCopy().id());
    }
}
