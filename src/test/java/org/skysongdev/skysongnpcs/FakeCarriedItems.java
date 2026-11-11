package org.skysongdev.skysongnpcs;

import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.sheets.CarriedItems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Citizens' Inventory and Equipment traits for tests: what each NPC carries. */
public final class FakeCarriedItems implements CarriedItems {
    private final Map<UUID, List<ItemStack>> carried = new HashMap<>();

    public void give(UUID npc, ItemStack... items) {
        carried.put(npc, List.of(items));
    }

    @Override
    public Optional<List<ItemStack>> carried(UUID npc) {
        return Optional.of(carried.getOrDefault(npc, List.of()));
    }
}
