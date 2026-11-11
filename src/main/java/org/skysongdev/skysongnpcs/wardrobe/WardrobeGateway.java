package org.skysongdev.skysongnpcs.wardrobe;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What SkysongNPCs asks SkysongWardrobe, so nothing else touches its classes and the plugin
 * runs without it. {@link SkysongWardrobeGateway} is the real one; {@link #NONE} stands in
 * when SkysongWardrobe is not installed, and tests use their own.
 */
public interface WardrobeGateway {

    /** Whether NPC wardrobes exist on this server: SkysongWardrobe is enabled and hooked into Citizens. */
    boolean available();

    /**
     * Opens the NPC's wardrobe for the viewer to dress it. The caller has already decided they
     * may, so SkysongWardrobe's own access rules are skipped.
     *
     * @param onClose runs one tick after the viewer closes it, or null for nothing
     */
    Opening openEditor(Player viewer, UUID npc, Consumer<Player> onClose);

    /** What the NPC wears, by wardrobe slot id, in the order wardrobe.yml lists the slots. */
    Map<String, ItemStack> outfit(UUID npc);

    /**
     * Makes SkysongWardrobe agree with {@code wardrobes} about managed NPCs: lets in everyone it
     * allows (for {@code /sw npc}) and stops everyone it refuses, whatever opens the wardrobe.
     */
    void guard(Plugin plugin, NpcWardrobes wardrobes);

    /** How opening an NPC's wardrobe went. */
    enum Opening {
        OPENED,
        /** Citizens has no such NPC. */
        NOT_FOUND,
        /** Only player NPCs wear a wardrobe. */
        NOT_A_PLAYER_NPC,
        /** Someone else is dressing the NPC right now. */
        IN_USE,
        /** wardrobe.yml has errors, so SkysongWardrobe keeps every wardrobe closed. */
        CLOSED,
        /** SkysongWardrobe is not installed, or not hooked into Citizens. */
        UNAVAILABLE,
        /** A plugin cancelled the opening and told the viewer why itself. */
        CANCELLED
    }

    /** No SkysongWardrobe: NPCs have no wardrobes and nothing opens. */
    WardrobeGateway NONE = new WardrobeGateway() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Opening openEditor(Player viewer, UUID npc, Consumer<Player> onClose) {
            return Opening.UNAVAILABLE;
        }

        @Override
        public Map<String, ItemStack> outfit(UUID npc) {
            return Map.of();
        }

        @Override
        public void guard(Plugin plugin, NpcWardrobes wardrobes) {
        }
    };
}
