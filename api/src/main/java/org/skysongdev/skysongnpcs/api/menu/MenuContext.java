package org.skysongdev.skysongnpcs.api.menu;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;

/**
 * One player looking at one NPC's menu. A button gets it to decide whether it shows, what
 * icon it draws, and what a click does.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface MenuContext {

    /**
     * @return the player the menu is open for
     */
    @NotNull Player viewer();

    /**
     * @return the NPC the menu is about, as it was when the menu was opened or last redrawn
     */
    @NotNull ManagedNpc npc();
}
