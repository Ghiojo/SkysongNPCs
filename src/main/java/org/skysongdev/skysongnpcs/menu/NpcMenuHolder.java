package org.skysongdev.skysongnpcs.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.HashMap;
import java.util.Map;

/** Marks an inventory as an open NPC menu and remembers which button sits in which slot. */
final class NpcMenuHolder implements InventoryHolder, MenuContext {
    private final Player viewer;
    private NpcRecord npc;
    private Inventory inventory;
    private final Map<Integer, RegisteredButton> buttons = new HashMap<>();

    NpcMenuHolder(Player viewer, NpcRecord npc) {
        this.viewer = viewer;
        this.npc = npc;
    }

    @Override
    public @NotNull Player viewer() {
        return viewer;
    }

    @Override
    public @NotNull NpcRecord npc() {
        return npc;
    }

    void npc(NpcRecord current) {
        npc = current;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void inventory(Inventory created) {
        inventory = created;
    }

    RegisteredButton button(int slot) {
        return buttons.get(slot);
    }

    void place(Map<Integer, RegisteredButton> placed) {
        buttons.clear();
        buttons.putAll(placed);
    }
}
