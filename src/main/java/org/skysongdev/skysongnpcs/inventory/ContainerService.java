package org.skysongdev.skysongnpcs.inventory;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.inventory.NpcInventories;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens an NPC's inventory or equipment for a player, by their access: Citizens' own editor
 * for an {@code -edit} action, a read-only copy for a {@code -view} one. Also the API's
 * {@link NpcInventories}.
 *
 * <p>The items an NPC carries count toward its stat sheet; the sheet's regular recount picks
 * up whatever an editor changed, so nothing here has to tell it.
 */
public final class ContainerService implements NpcInventories {
    /** The equipment copy's slots, left to right: armour from the head down, then hands, then animal gear. */
    static final List<EquipmentSlot> EQUIPMENT_ORDER = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.BODY, EquipmentSlot.SADDLE);

    private final NpcRegistry registry;
    private final NpcLookup citizens;
    private final NpcContainers containers;
    private final AccessService access;
    private final PluginConfiguration configuration;

    public ContainerService(NpcRegistry registry, NpcLookup citizens, NpcContainers containers,
                            AccessService access, PluginConfiguration configuration) {
        this.registry = registry;
        this.citizens = citizens;
        this.containers = containers;
        this.access = access;
        this.configuration = configuration;
    }

    /** Opens it and, when nothing opened, tells the player why. Used by the commands and the menu buttons. */
    public Opened openAndTell(Player viewer, UUID npc, Container container, boolean viewOnly) {
        Opened opened = open(viewer, npc, container, viewOnly);
        Map<String, String> placeholders = Map.of("npc", citizens.name(npc));
        switch (opened) {
            case NOT_ALLOWED -> send(viewer, "containers." + container.key() + ".not-allowed", placeholders);
            case NOT_PLACED -> send(viewer, "containers.not-placed", placeholders);
            case NOT_MANAGED -> send(viewer, "npc.not-managed", placeholders);
            default -> { }
        }
        return opened;
    }

    /** Opens it, checking the viewer's access. */
    public Opened open(Player viewer, UUID npc, Container container, boolean viewOnly) {
        Optional<NpcRecord> record = registry.get(npc);
        if (record.isEmpty() || !citizens.exists(npc)) {
            return Opened.NOT_MANAGED;
        }
        if (!viewOnly && access.may(viewer, record.get(), container.edit())) {
            if (!containers.spawned(npc)) {
                return Opened.NOT_PLACED;
            }
            if (container == Container.INVENTORY) {
                containers.editInventory(viewer, npc);
            } else {
                containers.editEquipment(viewer, npc);
            }
            return Opened.EDITOR;
        }
        if (!access.may(viewer, record.get(), container.view())) {
            return Opened.NOT_ALLOWED;
        }
        Optional<List<ItemStack>> items = container == Container.INVENTORY
                ? containers.inventory(npc) : containers.equipment(npc).map(ContainerService::equipmentRow);
        if (items.isEmpty()) {
            return Opened.NOT_MANAGED;
        }
        viewer.openInventory(copy(npc, container, items.get()));
        return Opened.VIEW;
    }

    /** Whether the viewer may at least see it, so the menu shows its button. */
    public boolean maySee(Player viewer, NpcRecord npc, Container container) {
        return access.may(viewer, npc, container.view());
    }

    /** Whether the viewer may change it, so the menu's button says so. */
    public boolean mayEdit(Player viewer, NpcRecord npc, Container container) {
        return access.may(viewer, npc, container.edit());
    }

    private Inventory copy(UUID npc, Container container, List<ItemStack> items) {
        ReadOnlyView holder = new ReadOnlyView(npc, container);
        // A chest holds 9 to 54 slots, in rows of 9.
        int size = Math.min(54, Math.max(9, (items.size() + 8) / 9 * 9));
        Inventory inventory = Bukkit.createInventory(holder, size, configuration.messages()
                .get("containers." + container.key() + ".view-title", Map.of("npc", citizens.name(npc))));
        holder.inventory(inventory);
        for (int slot = 0; slot < Math.min(size, items.size()); slot++) {
            ItemStack item = items.get(slot);
            if (item != null && !item.isEmpty()) {
                inventory.setItem(slot, item.clone());
            }
        }
        return inventory;
    }

    private static List<ItemStack> equipmentRow(Map<EquipmentSlot, ItemStack> equipment) {
        List<ItemStack> row = new ArrayList<>();
        for (EquipmentSlot slot : EQUIPMENT_ORDER) {
            row.add(equipment.get(slot));
        }
        return row;
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        configuration.messages().send(sender, key, placeholders);
    }

    // The API

    @Override
    public @NotNull Opened openInventory(@NotNull Player viewer, @NotNull UUID npc, boolean viewOnly) {
        mainThread();
        return open(Objects.requireNonNull(viewer, "viewer"), Objects.requireNonNull(npc, "npc"),
                Container.INVENTORY, viewOnly);
    }

    @Override
    public @NotNull Opened openEquipment(@NotNull Player viewer, @NotNull UUID npc, boolean viewOnly) {
        mainThread();
        return open(Objects.requireNonNull(viewer, "viewer"), Objects.requireNonNull(npc, "npc"),
                Container.EQUIPMENT, viewOnly);
    }

    @Override
    public @NotNull Optional<List<ItemStack>> inventory(@NotNull UUID npc) {
        mainThread();
        if (registry.get(Objects.requireNonNull(npc, "npc")).isEmpty()) {
            return Optional.empty();
        }
        return containers.inventory(npc).map(items -> items.stream()
                .map(item -> item == null || item.isEmpty() ? ItemStack.empty() : item.clone())
                .toList());
    }

    @Override
    public @NotNull Optional<Map<EquipmentSlot, ItemStack>> equipment(@NotNull UUID npc) {
        mainThread();
        if (registry.get(Objects.requireNonNull(npc, "npc")).isEmpty()) {
            return Optional.empty();
        }
        return containers.equipment(npc).map(Map::copyOf);
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
