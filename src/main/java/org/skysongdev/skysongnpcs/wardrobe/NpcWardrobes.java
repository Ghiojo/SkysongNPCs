package org.skysongdev.skysongnpcs.wardrobe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Managed NPCs' wardrobes. {@code wardrobe-edit} opens SkysongWardrobe's own menu to dress the
 * NPC; {@code wardrobe-view} opens a read-only menu of what it wears, since SkysongWardrobe
 * 1.0.0 has no view-only mode. Nobody opens the wardrobe of a picked-up ({@code STORED}) NPC.
 */
public final class NpcWardrobes {
    /** The most a chest menu holds. */
    private static final int MAX_SIZE = 54;

    private final Plugin plugin;
    private final NpcRegistry registry;
    private final NpcLookup citizens;
    private final AccessService access;
    private final NpcMenuService menu;
    private final PluginConfiguration configuration;
    private final WardrobeGateway wardrobe;

    public NpcWardrobes(Plugin plugin, NpcRegistry registry, NpcLookup citizens, AccessService access,
                        NpcMenuService menu, PluginConfiguration configuration, WardrobeGateway wardrobe) {
        this.plugin = plugin;
        this.registry = registry;
        this.citizens = citizens;
        this.access = access;
        this.menu = menu;
        this.configuration = configuration;
        this.wardrobe = wardrobe;
    }

    /** Whether NPCs have wardrobes on this server (SkysongWardrobe is installed). */
    public boolean available() {
        return wardrobe.available();
    }

    /**
     * Whether the viewer may at least look at the NPC's wardrobe now: what the menu button and
     * {@code /snpc wardrobe} need.
     */
    public boolean mayLook(Player viewer, NpcRecord npc) {
        return npc.state() != NpcState.STORED && access.may(viewer, npc, NpcAction.WARDROBE_VIEW);
    }

    /** Whether the viewer may dress the NPC now; false for NPCs SkysongNPCs does not manage. */
    public boolean mayDress(Player viewer, UUID npc) {
        return registry.get(npc).map(record -> dressRefusal(viewer, record).isEmpty()).orElse(false);
    }

    /**
     * Why the viewer may not dress the NPC, as a messages.yml key, or empty when they may.
     * NPCs SkysongNPCs does not manage are SkysongWardrobe's business, so always empty.
     */
    public Optional<String> dressRefusal(Player viewer, UUID npc) {
        return registry.get(npc).flatMap(record -> dressRefusal(viewer, record));
    }

    Optional<String> dressRefusal(Player viewer, NpcRecord npc) {
        if (npc.state() == NpcState.STORED) {
            return Optional.of("wardrobe.stored");
        }
        if (access.may(viewer, npc, NpcAction.WARDROBE_EDIT)) {
            return Optional.empty();
        }
        if (access.may(viewer, npc, NpcAction.WARDROBE_VIEW)) {
            return Optional.of("wardrobe.view-only");
        }
        return Optional.of("wardrobe.not-allowed");
    }

    /**
     * Opens the NPC's wardrobe the way the viewer may: to dress it, or to look at it. Tells them
     * why when neither.
     *
     * @param backToMenu reopen the NPC menu when they close the wardrobe
     * @return whether a wardrobe opened
     */
    public boolean open(Player viewer, NpcRecord npc, boolean backToMenu) {
        if (!wardrobe.available()) {
            tell(viewer, "wardrobe.unavailable", npc.id());
            return false;
        }
        Optional<String> refusal = dressRefusal(viewer, npc);
        if (refusal.isEmpty()) {
            return openEditor(viewer, npc, backToMenu);
        }
        if (refusal.get().equals("wardrobe.view-only")) {
            return openView(viewer, npc, backToMenu);
        }
        tell(viewer, refusal.get(), npc.id());
        return false;
    }

    private boolean openEditor(Player viewer, NpcRecord npc, boolean backToMenu) {
        Consumer<Player> onClose = backToMenu ? player -> backToMenu(player, npc.id()) : null;
        WardrobeGateway.Opening opening = wardrobe.openEditor(viewer, npc.id(), onClose);
        String key = switch (opening) {
            case OPENED, CANCELLED -> null;
            case NOT_FOUND -> "wardrobe.not-found";
            case NOT_A_PLAYER_NPC -> "wardrobe.not-a-player-npc";
            case IN_USE -> "wardrobe.in-use";
            case CLOSED -> "wardrobe.closed";
            case UNAVAILABLE -> "wardrobe.unavailable";
        };
        if (key != null) {
            tell(viewer, key, npc.id());
        }
        return opening == WardrobeGateway.Opening.OPENED;
    }

    /** The read-only menu: one item per worn wardrobe slot, in wardrobe.yml's order. */
    boolean openView(Player viewer, NpcRecord npc, boolean backToMenu) {
        List<ItemStack> worn = wardrobe.outfit(npc.id()).values().stream()
                .filter(item -> item != null && !item.isEmpty())
                .limit(MAX_SIZE)
                .toList();
        if (worn.isEmpty()) {
            tell(viewer, "wardrobe.nothing-worn", npc.id());
            return false;
        }
        int size = Math.max(9, (worn.size() + 8) / 9 * 9);
        WardrobeViewHolder holder = new WardrobeViewHolder(npc.id(), backToMenu);
        Inventory inventory = Bukkit.createInventory(holder, size,
                configuration.messages().get("wardrobe.view-title", names(npc.id())));
        holder.inventory(inventory);
        for (int slot = 0; slot < worn.size(); slot++) {
            inventory.setItem(slot, worn.get(slot).clone());
        }
        viewer.openInventory(inventory);
        return true;
    }

    /** Opens the NPC menu again a tick after a wardrobe closes, unless the viewer is in another menu by then. */
    void backToMenu(Player viewer, UUID npc) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline() && viewer.getOpenInventory().getType() == InventoryType.CRAFTING) {
                menu.open(viewer, npc);
            }
        });
    }

    /** Sends a {@code wardrobe.*} message about the NPC. */
    public void tell(Player viewer, String key, UUID npc) {
        configuration.messages().send(viewer, key, names(npc));
    }

    private Map<String, String> names(UUID npc) {
        return Map.of("npc", citizens.name(npc));
    }
}
