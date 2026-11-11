package org.skysongdev.skysongnpcs.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the plugin asks Citizens about an NPC's Inventory and Equipment traits.
 * {@link org.skysongdev.skysongnpcs.citizens.CitizensNpcContainers} is the real one; tests use
 * their own, since Citizens is not on the test classpath.
 */
public interface NpcContainers {

    /**
     * Copies of the inventory's slots, in order; an empty slot is null.
     *
     * @return empty when Citizens has no such NPC
     */
    Optional<List<ItemStack>> inventory(UUID npc);

    /**
     * Copies of what the NPC wears and holds, by slot; empty slots are left out.
     *
     * @return empty when Citizens has no such NPC
     */
    Optional<Map<EquipmentSlot, ItemStack>> equipment(UUID npc);

    /** Whether the NPC is in the world now, which Citizens' editors need. */
    boolean spawned(UUID npc);

    /** Opens Citizens' own inventory editor, which saves the items when it closes. The NPC is spawned. */
    void editInventory(Player player, UUID npc);

    /** Opens Citizens' own equipment editor, which saves the items when it closes. The NPC is spawned. */
    void editEquipment(Player player, UUID npc);
}
