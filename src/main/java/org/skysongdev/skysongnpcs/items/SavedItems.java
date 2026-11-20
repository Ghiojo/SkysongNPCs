package org.skysongdev.skysongnpcs.items;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;

/**
 * SkysongItems' saved items, the files {@code /si saveitem} writes, by the ids
 * {@code /si loaditem} takes ({@code npc_token}, or {@code npcs/npc_token} for one saved into a
 * folder). {@link SkysongSavedItems} is the real one; tests use their own, since SkysongItems
 * is not on the test classpath.
 */
public interface SavedItems {

    /** The ids of every saved item. */
    Set<String> ids();

    /** A new item made from a saved item, like {@code /si loaditem}; empty when there is no saved item with this id. */
    Optional<ItemStack> create(String id);
}
