package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The abilities menu: one icon per ability of the NPC, its description as lore. It only
 * shows; every click is cancelled. Opened from the NPC menu's Abilities button and with
 * {@code /snpc abilities}.
 */
public final class AbilitiesMenu implements Listener {
    private static final int MAX_ROWS = 6;

    private final NpcRegistry registry;
    private final NpcLookup citizens;
    private final AbilityService abilities;
    private final AbilityText text;
    private final PluginConfiguration configuration;

    public AbilitiesMenu(NpcRegistry registry, NpcLookup citizens, AbilityService abilities, AbilityText text,
                         PluginConfiguration configuration) {
        this.registry = registry;
        this.citizens = citizens;
        this.abilities = abilities;
        this.text = text;
        this.configuration = configuration;
    }

    /**
     * Opens the NPC's abilities for a player, without checking the player's access.
     *
     * @return false when the NPC is not managed
     */
    public boolean open(Player viewer, UUID npc) {
        if (!registry.isManaged(npc)) {
            return false;
        }
        String name = citizens.name(npc);
        List<AbilityDefinition> owned = abilities.of(npc);
        int shown = Math.min(owned.size(), MAX_ROWS * 9);
        int rows = Math.max(1, (shown + 8) / 9);
        Holder holder = new Holder(npc);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                configuration.messages().get("abilities.menu.title", Map.of("npc", name)));
        holder.inventory = inventory;
        if (owned.isEmpty()) {
            inventory.setItem(4, text.none(name));
        }
        for (int slot = 0; slot < shown; slot++) {
            inventory.setItem(slot, text.icon(owned.get(slot)));
        }
        viewer.openInventory(inventory);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    /** Marks an inventory as an open abilities menu. */
    static final class Holder implements InventoryHolder {
        private final UUID npc;
        private Inventory inventory;

        private Holder(UUID npc) {
            this.npc = npc;
        }

        UUID npc() {
            return npc;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
