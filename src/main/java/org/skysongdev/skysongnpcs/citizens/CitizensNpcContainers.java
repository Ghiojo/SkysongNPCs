package org.skysongdev.skysongnpcs.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Inventory;
import net.citizensnpcs.editor.Editor;
import net.citizensnpcs.editor.EquipmentEditor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.inventory.NpcContainers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and edits an NPC's Citizens Inventory and Equipment traits. Editing uses the same
 * editors as Citizens' {@code /npc inventory} and {@code /npc edit equip}, called directly, so
 * Citizens' own command permissions are not involved.
 */
public final class CitizensNpcContainers implements NpcContainers {
    /** What an NPC without an Inventory trait shows: an empty player-sized inventory. */
    private static final int EMPTY_INVENTORY_SIZE = 36;

    @Override
    public Optional<List<ItemStack>> inventory(UUID npc) {
        NPC found = find(npc);
        if (found == null) {
            return Optional.empty();
        }
        ItemStack[] contents = found.hasTrait(Inventory.class)
                ? found.getOrAddTrait(Inventory.class).getContents() : new ItemStack[EMPTY_INVENTORY_SIZE];
        List<ItemStack> copy = new ArrayList<>(contents.length);
        for (ItemStack item : contents) {
            copy.add(item == null || item.getType().isAir() ? null : item.clone());
        }
        return Optional.of(copy);
    }

    @Override
    public Optional<Map<EquipmentSlot, ItemStack>> equipment(UUID npc) {
        NPC found = find(npc);
        if (found == null) {
            return Optional.empty();
        }
        Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
        if (found.hasTrait(Equipment.class)) {
            found.getOrAddTrait(Equipment.class).getEquipmentBySlot().forEach((slot, item) -> {
                EquipmentSlot bukkit = slot.toBukkit();
                if (bukkit != null && item != null && !item.getType().isAir()) {
                    worn.put(bukkit, item.clone());
                }
            });
        }
        return Optional.of(worn);
    }

    @Override
    public boolean spawned(UUID npc) {
        NPC found = find(npc);
        return found != null && found.isSpawned();
    }

    @Override
    public void editInventory(Player player, UUID npc) {
        NPC found = find(npc);
        if (found != null) {
            found.getOrAddTrait(Inventory.class).openInventory(player);
        }
    }

    @Override
    public void editEquipment(Player player, UUID npc) {
        NPC found = find(npc);
        if (found == null) {
            return;
        }
        // enterOrLeave toggles: a player already in an editor would only leave it, so leave first.
        if (Editor.hasEditor(player)) {
            Editor.leave(player);
        }
        Editor.enterOrLeave(player, new EquipmentEditor(player, found));
    }

    private static NPC find(UUID npc) {
        return CitizensAPI.getNPCRegistry().getByUniqueId(npc);
    }
}
