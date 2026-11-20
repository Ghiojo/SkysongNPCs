package org.skysongdev.skysongnpcs;

import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.items.SavedItems;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** SkysongItems' saved items for tests: each id makes a clone of the item saved under it. */
public final class FakeSavedItems implements SavedItems {
    private final Map<String, ItemStack> items = new LinkedHashMap<>();

    /** Saves an item under an id, like {@code /si saveitem}, replacing what it had. */
    public void save(String id, ItemStack item) {
        items.put(id, item.clone());
    }

    /** Deletes a saved item. */
    public void delete(String id) {
        items.remove(id);
    }

    @Override
    public Set<String> ids() {
        return Set.copyOf(items.keySet());
    }

    @Override
    public Optional<ItemStack> create(String id) {
        return Optional.ofNullable(items.get(id)).map(ItemStack::clone);
    }
}
