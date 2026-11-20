package org.skysongdev.skysongnpcs.items;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Where the NPC item's own lore lines (messages.yml's {@code pickup.item.lore}) go on the
 * item, config.yml's {@code pickup.lore-position}: after the trait lines, or right above or
 * below one of the item's three description sections. SkysongItems keeps them there through
 * every redraw of the item's lore.
 */
public enum NpcLorePosition {
    AFTER_TRAITS,
    ABOVE_PHYSICAL,
    BELOW_PHYSICAL,
    ABOVE_FUNCTIONAL,
    BELOW_FUNCTIONAL,
    ABOVE_SKILL,
    BELOW_SKILL;

    /** As config.yml writes it: {@code after-traits}, {@code below-physical}... */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The position config.yml names; AFTER_TRAITS when it is missing or not one of them. */
    public static NpcLorePosition parse(@Nullable String value) {
        if (value != null) {
            String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            for (NpcLorePosition position : values()) {
                if (position.name().equals(key)) {
                    return position;
                }
            }
        }
        return AFTER_TRAITS;
    }
}
