package org.skysongdev.skysongnpcs.citizens;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.editor.Editor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;

/**
 * Right-clicking a managed NPC opens its menu. When the player would see no button, or is in
 * one of Citizens' editors ({@code /npc edit ...}), the click is left alone, so Citizens' own
 * click commands and other plugins still get it.
 */
public final class CitizensMenuListener implements Listener {
    private final NpcMenuService menu;

    public CitizensMenuListener(NpcMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(NPCRightClickEvent event) {
        if (!menu.opensOnRightClick() || Editor.hasEditor(event.getClicker())) {
            return;
        }
        if (menu.open(event.getClicker(), event.getNPC().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
