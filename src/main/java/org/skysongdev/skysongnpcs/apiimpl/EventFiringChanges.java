package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.plugin.PluginManager;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;
import org.skysongdev.skysongnpcs.api.event.NpcOwnerChangeEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

/** Turns the registry's changes into the API's events. */
public final class EventFiringChanges implements NpcRegistry.Changes {
    private final PluginManager plugins;

    public EventFiringChanges(PluginManager plugins) {
        this.plugins = plugins;
    }

    @Override
    public void ownerChanged(NpcRecord npc, NpcOwner previous) {
        plugins.callEvent(new NpcOwnerChangeEvent(npc, previous));
    }

    @Override
    public void cloned(NpcRecord original, NpcRecord copy) {
        plugins.callEvent(new NpcCloneEvent(original, copy));
    }

    @Override
    public void deleted(NpcRecord npc, Deletion why) {
        NpcDeleteEvent.Cause cause = switch (why) {
            case REMOVED -> NpcDeleteEvent.Cause.REMOVED;
            case RELEASED -> NpcDeleteEvent.Cause.RELEASED;
        };
        plugins.callEvent(new NpcDeleteEvent(npc, cause));
    }
}
