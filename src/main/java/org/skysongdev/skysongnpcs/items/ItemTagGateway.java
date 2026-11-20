package org.skysongdev.skysongnpcs.items;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * The SkysongItems tags blueprints are read from and written to, and the lore lines
 * SkysongNPCs keeps on NPC items. {@link SkysongItemsTagGateway}
 * is the real one; tests use their own, since SkysongItems is not on the test classpath.
 */
public interface ItemTagGateway {

    /** A string tag's value, trimmed; empty for null or air items, a missing tag or a blank value. */
    Optional<String> string(ItemStack item, String tag);

    /** Writes a string tag on an item nobody holds yet (SkysongItems' detached write). */
    void setDetached(ItemStack item, String tag, String value);

    /**
     * Makes the item a SkysongItems item of the current version, if it is not one, and sets
     * SkysongNPCs' own lore lines on it at {@code position}, which SkysongItems keeps through
     * every redraw of the item's lore. Replaces lines set before; an empty list removes them.
     */
    void setLore(ItemStack item, NpcLorePosition position, List<Component> lines);
}
