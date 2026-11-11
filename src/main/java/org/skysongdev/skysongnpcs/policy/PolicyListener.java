package org.skysongdev.skysongnpcs.policy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;

/** A deleted NPC leaves its policy; a copied one follows the same policy as its original. */
public final class PolicyListener implements Listener {
    private final PolicyRegistry policies;

    public PolicyListener(PolicyRegistry policies) {
        this.policies = policies;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDelete(NpcDeleteEvent event) {
        policies.forget(event.getNpc().id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClone(NpcCloneEvent event) {
        policies.copy(event.getOriginal().id(), event.getCopy().id());
    }
}
