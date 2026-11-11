package org.skysongdev.skysongnpcs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.wardrobe.NpcWardrobes;
import org.skysongdev.skysongnpcs.wardrobe.WardrobeGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** SkysongWardrobe as the test sets it up: outfits by NPC, and a record of what was opened. */
public final class FakeWardrobe implements WardrobeGateway {
    private final Map<UUID, Map<String, ItemStack>> outfits = new HashMap<>();
    private final List<UUID> opened = new ArrayList<>();
    private boolean available = true;
    private Opening result = Opening.OPENED;
    private Consumer<Player> lastOnClose;

    public void available(boolean value) {
        available = value;
    }

    public void result(Opening value) {
        result = value;
    }

    public void wear(UUID npc, String slot, ItemStack item) {
        outfits.computeIfAbsent(npc, id -> new LinkedHashMap<>()).put(slot, item);
    }

    /** The NPCs whose wardrobe editor opened, in order. */
    public List<UUID> opened() {
        return opened;
    }

    /** What the last editor opened would run on close. */
    public Consumer<Player> lastOnClose() {
        return lastOnClose;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public Opening openEditor(Player viewer, UUID npc, Consumer<Player> onClose) {
        lastOnClose = onClose;
        if (result == Opening.OPENED) {
            opened.add(npc);
        }
        return result;
    }

    @Override
    public Map<String, ItemStack> outfit(UUID npc) {
        return outfits.getOrDefault(npc, Map.of());
    }

    @Override
    public void guard(Plugin plugin, NpcWardrobes wardrobes) {
        // SkysongWardrobe's rule and event are not on the test classpath; NpcWardrobes' answers are tested directly.
    }
}
