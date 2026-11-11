package org.skysongdev.skysongnpcs;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.skysongdev.skysongnpcs.items.ItemTagGateway;

import java.util.Locale;
import java.util.Optional;

/** SkysongItems' tags for tests: each tag is a string in the item's persistent data. */
public final class FakeItemTags implements ItemTagGateway {

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

    private static NamespacedKey key(String tag) {
        return new NamespacedKey("skysongitems_test", tag.toLowerCase(Locale.ROOT));
    }
}
