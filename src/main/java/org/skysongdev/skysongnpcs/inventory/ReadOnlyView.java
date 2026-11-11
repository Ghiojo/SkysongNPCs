package org.skysongdev.skysongnpcs.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marks an inventory as a read-only copy of an NPC's inventory or equipment. Nothing put in or
 * taken out of it would reach the NPC, so {@link ReadOnlyViewListener} lets nothing move.
 */
public final class ReadOnlyView implements InventoryHolder {
    private final UUID npc;
    private final Container container;
    private Inventory inventory;

    ReadOnlyView(UUID npc, Container container) {
        this.npc = npc;
        this.container = container;
    }

    public UUID npc() {
        return npc;
    }

    public Container container() {
        return container;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void inventory(Inventory created) {
        inventory = created;
    }
}
