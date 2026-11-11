package org.skysongdev.skysongnpcs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.inventory.NpcContainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Citizens' Inventory and Equipment traits for tests: what each NPC holds, whether it is
 * spawned, and which editors were opened for whom.
 */
public final class FakeNpcContainers implements NpcContainers {
    private final Map<UUID, ItemStack[]> inventories = new HashMap<>();
    private final Map<UUID, Map<EquipmentSlot, ItemStack>> equipment = new HashMap<>();
    private final Set<UUID> despawned = new HashSet<>();
    private final List<String> editorsOpened = new ArrayList<>();

    /** Sets the NPC's 36 inventory slots, starting from the first. */
    public void carry(UUID npc, ItemStack... items) {
        inventories.put(npc, Arrays.copyOf(items, 36));
    }

    public void wear(UUID npc, EquipmentSlot slot, ItemStack item) {
        equipment.computeIfAbsent(npc, any -> new EnumMap<>(EquipmentSlot.class)).put(slot, item);
    }

    public void despawn(UUID npc) {
        despawned.add(npc);
    }

    /** "inventory Alice" or "equipment Alice" for every editor opened, in order. */
    public List<String> editorsOpened() {
        return editorsOpened;
    }

    @Override
    public Optional<List<ItemStack>> inventory(UUID npc) {
        return Optional.of(Arrays.asList(inventories.getOrDefault(npc, new ItemStack[36]).clone()));
    }

    @Override
    public Optional<Map<EquipmentSlot, ItemStack>> equipment(UUID npc) {
        return Optional.of(new EnumMap<>(equipment.getOrDefault(npc, Map.of())));
    }

    @Override
    public boolean spawned(UUID npc) {
        return !despawned.contains(npc);
    }

    @Override
    public void editInventory(Player player, UUID npc) {
        editorsOpened.add("inventory " + player.getName());
    }

    @Override
    public void editEquipment(Player player, UUID npc) {
        editorsOpened.add("equipment " + player.getName());
    }
}
