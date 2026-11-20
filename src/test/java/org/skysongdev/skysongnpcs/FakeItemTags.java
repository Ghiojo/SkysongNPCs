package org.skysongdev.skysongnpcs;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.skysongdev.skysongnpcs.items.ItemTagGateway;
import org.skysongdev.skysongnpcs.items.NpcLorePosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** SkysongItems' tags for tests: each tag is a string in the item's persistent data. */
public final class FakeItemTags implements ItemTagGateway {
    private static final NamespacedKey LORE_COUNT = new NamespacedKey("skysongitems_test", "npc_lore_count");
    private static final NamespacedKey LORE_POSITION = new NamespacedKey("skysongitems_test", "npc_lore_position");

    @Override
    public Optional<String> string(ItemStack item, String tag) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key(tag), PersistentDataType.STRING);
        return Optional.ofNullable(value).map(String::trim).filter(text -> !text.isEmpty());
    }

    @Override
    public void setDetached(ItemStack item, String tag, String value) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key(tag), PersistentDataType.STRING, value));
    }

    /**
     * Appends the lines to the item's lore, replacing the ones set before, and remembers the
     * position (read it with {@link #lorePosition}); SkysongItems would keep them through redraws.
     */
    @Override
    public void setLore(ItemStack item, NpcLorePosition position, List<Component> lines) {
        item.editMeta(meta -> {
            List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
            Integer before = meta.getPersistentDataContainer().get(LORE_COUNT, PersistentDataType.INTEGER);
            if (before != null) {
                lore = new ArrayList<>(lore.subList(0, lore.size() - before));
            }
            lore.addAll(lines);
            meta.lore(lore);
            meta.getPersistentDataContainer().set(LORE_COUNT, PersistentDataType.INTEGER, lines.size());
            meta.getPersistentDataContainer().set(LORE_POSITION, PersistentDataType.STRING, position.name());
        });
    }

    /** Where {@link #setLore} put the item's lines; empty when it has none. */
    public static Optional<NpcLorePosition> lorePosition(ItemStack item) {
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer().get(LORE_POSITION, PersistentDataType.STRING))
                .map(NpcLorePosition::valueOf);
    }

    private static NamespacedKey key(String tag) {
        return new NamespacedKey("skysongitems_test", tag.toLowerCase(Locale.ROOT));
    }
}
