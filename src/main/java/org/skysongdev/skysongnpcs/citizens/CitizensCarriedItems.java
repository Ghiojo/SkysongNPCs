package org.skysongdev.skysongnpcs.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Inventory;
import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.sheets.CarriedItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads an NPC's Citizens Inventory and Equipment traits. Both keep their items while the
 * NPC is despawned, so a stored NPC still counts what it carries. Only NPCs that have the
 * traits are read; none are added.
 */
public final class CitizensCarriedItems implements CarriedItems {

    @Override
    public Optional<List<ItemStack>> carried(UUID npc) {
        NPC found = CitizensAPI.getNPCRegistry().getByUniqueId(npc);
        if (found == null) {
            return Optional.empty();
        }
        List<ItemStack> items = new ArrayList<>();
        // The Inventory trait reads a player NPC's storage slots only, so armour is not counted twice.
        if (found.hasTrait(Inventory.class)) {
            add(items, found.getOrAddTrait(Inventory.class).getContents());
        }
        if (found.hasTrait(Equipment.class)) {
            add(items, found.getOrAddTrait(Equipment.class).getEquipment());
        }
        return Optional.of(items);
    }

    private static void add(List<ItemStack> items, ItemStack[] slots) {
        for (ItemStack item : slots) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
    }
}
