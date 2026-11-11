package org.skysongdev.skysongnpcs.wardrobe;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Marks an inventory as the read-only view of an NPC's wardrobe. */
final class WardrobeViewHolder implements InventoryHolder {
    private final UUID npc;
    private final boolean backToMenu;
    private Inventory inventory;

    WardrobeViewHolder(UUID npc, boolean backToMenu) {
        this.npc = npc;
        this.backToMenu = backToMenu;
    }

    UUID npc() {
        return npc;
    }

    boolean backToMenu() {
        return backToMenu;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void inventory(Inventory created) {
        inventory = created;
    }
}
