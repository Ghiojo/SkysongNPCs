package org.skysongdev.skysongnpcs.items;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongitems.api.ExtraLorePosition;
import org.skysongdev.skysongitems.api.LoreSection;
import org.skysongdev.skysongitems.api.SkysongItemsApi;

import java.util.List;
import java.util.Optional;

/** {@link ItemTagGateway} over the SkysongItems API, looked up on every call so a reload of it is seen. */
public final class SkysongItemsTagGateway implements ItemTagGateway {
    private final Plugin plugin;

    /** @param plugin owns the lore lines SkysongNPCs puts on items */
    public SkysongItemsTagGateway(Plugin plugin) {
        this.plugin = plugin;
    }

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

    @Override
    public void setLore(ItemStack item, NpcLorePosition position, List<Component> lines) {
        SkysongItemsApi api = SkysongItemsApi.get();
        // A plain item (the pickup.item material, or a blueprint made from a plain item) or one
        // from an older SkysongItems becomes a current SkysongItems item, so its lore is drawn now.
        api.templates().toSkysongItem(item);
        api.lore().setExtra(plugin, item, extraPosition(position), lines);
    }

    private static ExtraLorePosition extraPosition(NpcLorePosition position) {
        return switch (position) {
            case AFTER_TRAITS -> ExtraLorePosition.afterTraits();
            case ABOVE_PHYSICAL -> ExtraLorePosition.above(LoreSection.PHYSICAL);
            case BELOW_PHYSICAL -> ExtraLorePosition.below(LoreSection.PHYSICAL);
            case ABOVE_FUNCTIONAL -> ExtraLorePosition.above(LoreSection.FUNCTIONAL);
            case BELOW_FUNCTIONAL -> ExtraLorePosition.below(LoreSection.FUNCTIONAL);
            case ABOVE_SKILL -> ExtraLorePosition.above(LoreSection.SKILL);
            case BELOW_SKILL -> ExtraLorePosition.below(LoreSection.SKILL);
        };
    }
}
