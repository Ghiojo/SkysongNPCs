package org.skysongdev.skysongnpcs.policy;

import org.bukkit.plugin.PluginManager;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.event.AccessPolicyChangeEvent;
import org.skysongdev.skysongnpcs.api.event.NpcPolicyChangeEvent;

import java.util.Set;
import java.util.UUID;

/** Turns the policy registry's changes into the API's events. */
public final class EventFiringPolicyChanges implements PolicyRegistry.Changes {
    private final PluginManager plugins;

    public EventFiringPolicyChanges(PluginManager plugins) {
        this.plugins = plugins;
    }

    @Override
    public void grantChanged(String policy, AccessGroup group, Set<NpcAction> previous, Set<NpcAction> current) {
        plugins.callEvent(new AccessPolicyChangeEvent(policy, group, previous, current));
    }

    @Override
    public void membershipChanged(UUID npc, String previous, String current) {
        plugins.callEvent(new NpcPolicyChangeEvent(npc, previous, current));
    }
}
