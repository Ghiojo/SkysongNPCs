package org.skysongdev.skysongnpcs.sheets;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The items an NPC carries, for counting toward its sheet's item-tag stats.
 * {@link org.skysongdev.skysongnpcs.citizens.CitizensCarriedItems} is the real one; tests use
 * their own, since Citizens is not on the test classpath.
 */
public interface CarriedItems {

    /**
     * Its inventory and equipment, empty slots left out.
     *
     * @return empty when Citizens has no such NPC
     */
    Optional<List<ItemStack>> carried(UUID npc);
}
