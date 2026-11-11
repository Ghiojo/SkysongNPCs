package org.skysongdev.skysongnpcs.wardrobe;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongwardrobe.api.Access;
import org.skysongdev.skysongwardrobe.api.OpenOptions;
import org.skysongdev.skysongwardrobe.api.SkysongWardrobeApi;
import org.skysongdev.skysongwardrobe.api.Wearer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * NPC wardrobes through the SkysongWardrobe 1.0.0 API. Only built when SkysongWardrobe is
 * enabled, so its classes are never loaded without it.
 */
public final class SkysongWardrobeGateway implements WardrobeGateway {
    private final SkysongWardrobeApi wardrobe;

    private SkysongWardrobeGateway(SkysongWardrobeApi wardrobe) {
        this.wardrobe = wardrobe;
    }

    /** The gateway to the enabled SkysongWardrobe, or {@link #NONE} when it offers no API. */
    public static WardrobeGateway hook(Plugin plugin) {
        SkysongWardrobeApi api = plugin.getServer().getServicesManager().load(SkysongWardrobeApi.class);
        if (api == null) {
            plugin.getLogger().warning("SkysongWardrobe is enabled but offers no API, so NPCs have no wardrobes.");
            return NONE;
        }
        return new SkysongWardrobeGateway(api);
    }

    @Override
    public boolean available() {
        return wardrobe.npcs().available();
    }

    @Override
    public Opening openEditor(Player viewer, UUID npc, Consumer<Player> onClose) {
        // SkysongNPCs has decided already, and explains a refusal in its own words.
        OpenOptions options = OpenOptions.defaults().access(Access.BYPASS).silent();
        if (onClose != null) {
            options = options.onClose(onClose);
        }
        return switch (wardrobe.menus().openNpcWardrobe(viewer, npc, options)) {
            case OPENED -> Opening.OPENED;
            case NOT_FOUND -> Opening.NOT_FOUND;
            case NOT_A_PLAYER_NPC -> Opening.NOT_A_PLAYER_NPC;
            case IN_USE -> Opening.IN_USE;
            case WARDROBE_CLOSED -> Opening.CLOSED;
            case UNAVAILABLE -> Opening.UNAVAILABLE;
            // NO_ACCESS never comes back with BYPASS.
            case NO_ACCESS, CANCELLED -> Opening.CANCELLED;
        };
    }

    @Override
    public Map<String, ItemStack> outfit(UUID npc) {
        Map<String, ItemStack> worn = wardrobe.outfits().outfit(Wearer.npc(npc));
        Map<String, ItemStack> ordered = new LinkedHashMap<>();
        for (String slot : wardrobe.outfits().slots()) {
            ItemStack item = worn.get(slot);
            if (item != null) {
                ordered.put(slot, item);
            }
        }
        // A slot that left wardrobe.yml still shows, last.
        worn.forEach(ordered::putIfAbsent);
        return ordered;
    }

    @Override
    public void guard(Plugin plugin, NpcWardrobes wardrobes) {
        // Rules only let people in; SkysongWardrobe drops them when SkysongNPCs disables.
        wardrobe.npcs().registerAccessRule(plugin, (viewer, npc) -> wardrobes.mayDress(viewer, npc.id()));
        plugin.getServer().getPluginManager().registerEvents(new WardrobeOpenGuard(wardrobes), plugin);
    }
}
