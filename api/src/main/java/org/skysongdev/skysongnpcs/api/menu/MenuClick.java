package org.skysongdev.skysongnpcs.api.menu;

import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A click on a button in an NPC's menu. The click itself is already cancelled, so the icon
 * never moves into the player's inventory.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface MenuClick extends MenuContext {

    /**
     * @return how the button was clicked (left, right, shift...)
     */
    @NotNull ClickType click();

    /** Closes the menu on the next tick, the safe moment to open something else or send chat. */
    void close();

    /** Draws the menu again, asking every button whether it shows and for its icon. */
    void refresh();
}
