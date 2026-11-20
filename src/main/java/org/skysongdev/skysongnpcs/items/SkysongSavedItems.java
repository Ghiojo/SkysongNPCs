package org.skysongdev.skysongnpcs.items;

import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongitems.api.SkysongItemsApi;

import java.util.Optional;
import java.util.Set;

/** {@link SavedItems} over the SkysongItems API, looked up on every call so a reload of it is seen. */
public final class SkysongSavedItems implements SavedItems {

    @Override
    public Set<String> ids() {
        return SkysongItemsApi.get().templates().ids();
    }

    @Override
    public Optional<ItemStack> create(String id) {
        return SkysongItemsApi.get().templates().create(id);
    }
}
