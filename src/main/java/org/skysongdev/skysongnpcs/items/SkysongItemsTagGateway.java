package org.skysongdev.skysongnpcs.items;

import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongitems.api.SkysongItemsApi;

import java.util.Optional;

/** {@link ItemTagGateway} over the SkysongItems API, looked up on every call so a reload of it is seen. */
public final class SkysongItemsTagGateway implements ItemTagGateway {

    @Override
    public Optional<String> string(ItemStack item, String tag) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return SkysongItemsApi.get().tags().getString(item, tag).map(String::trim).filter(value -> !value.isEmpty());
    }

    @Override
    public void setDetached(ItemStack item, String tag, String value) {
        SkysongItemsApi.get().detached().tags().set(item, tag, value);
    }
}
