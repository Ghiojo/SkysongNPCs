package org.skysongdev.skysongnpcs.citizens;

import net.citizensnpcs.api.event.NPCCloneEvent;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

/**
 * Keeps records in step with Citizens: a removed NPC loses its record, and a copy of a
 * managed NPC gets a record of its own.
 *
 * <p>Citizens fires {@link NPCRemoveEvent} only when an NPC is destroyed for good
 * ({@code /npc remove}, {@code NPC#destroy()}); despawning, reloads and shutdown do not, so
 * records survive restarts. {@code /npc remove} also fires {@code NPCRemoveByCommandSenderEvent}
 * first, then this one, so listening here catches every removal once.
 */
public final class CitizensListener implements Listener {
    private final NpcRegistry registry;

    public CitizensListener(NpcRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(NPCRemoveEvent event) {
        registry.removed(event.getNPC().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCopy(NPCCloneEvent event) {
        registry.copied(event.getNPC().getUniqueId(), event.getClone().getUniqueId());
    }
}
