package org.skysongdev.skysongnpcs.items;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * The SkysongItems tags blueprints are read from and written to. {@link SkysongItemsTagGateway}
 * is the real one; tests use their own, since SkysongItems is not on the test classpath.
 */
public interface ItemTagGateway {

    /** A string tag's value, trimmed; empty for null or air items, a missing tag or a blank value. */
    Optional<String> string(ItemStack item, String tag);

    /** Writes a string tag on an item nobody holds yet (SkysongItems' detached write). */
    void setDetached(ItemStack item, String tag, String value);
}
