package org.skysongdev.skysongnpcs.choices;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;

/**
 * Keeps choices in step with the NPC records: a deleted NPC's points and setup steps go, and
 * a copied NPC gets its own copy of them (unspent points stay unspent, spent ones stay spent).
 */
public final class ChoiceLifecycleListener implements Listener {
    private final StatPoints points;
    private final SetupFlow setup;

    public ChoiceLifecycleListener(StatPoints points, SetupFlow setup) {
        this.points = points;
        this.setup = setup;
    }

    @EventHandler
    public void onDelete(NpcDeleteEvent event) {
        points.forget(event.getNpc().id());
        setup.forget(event.getNpc().id());
    }

    @EventHandler
    public void onClone(NpcCloneEvent event) {
        points.copy(event.getOriginal().id(), event.getCopy().id());
        setup.copy(event.getOriginal().id(), event.getCopy().id());
    }
}
