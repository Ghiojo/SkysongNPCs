package org.skysongdev.skysongnpcs.wardrobe;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.skysongdev.skysongwardrobe.api.WearerType;
import org.skysongdev.skysongwardrobe.api.event.WardrobeOpenEvent;

/**
 * Keeps a managed NPC's wardrobe closed for anyone SkysongNPCs refuses, however it is opened:
 * {@code /sw npc}, the Citizens owner, or another plugin. SkysongWardrobe stays silent on a
 * cancel, so the viewer is told why here.
 */
final class WardrobeOpenGuard implements Listener {
    private final NpcWardrobes wardrobes;

    WardrobeOpenGuard(NpcWardrobes wardrobes) {
        this.wardrobes = wardrobes;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpen(WardrobeOpenEvent event) {
        if (event.getWearer().type() != WearerType.NPC) {
            return;
        }
        wardrobes.dressRefusal(event.getViewer(), event.getWearer().id()).ifPresent(refusal -> {
            event.setCancelled(true);
            wardrobes.tell(event.getViewer(), refusal, event.getWearer().id());
        });
    }
}
