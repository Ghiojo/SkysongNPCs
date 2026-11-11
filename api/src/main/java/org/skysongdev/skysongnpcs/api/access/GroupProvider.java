package org.skysongdev.skysongnpcs.api.access;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Groups another plugin keeps, such as SkysongEconomy's business groups, so grants can name
 * them as {@code <type>:<id>}. Register one with {@link Access#registerGroupProvider}.
 * SkysongNPCs asks it live every time, so the plugin never has to tell us when someone joins
 * or leaves a group.
 *
 * <p>Called on the main thread, often (every right-click on an NPC), so answer from memory.
 */
public interface GroupProvider {

    /**
     * Whether a player, playing a character, is in one of this provider's groups.
     *
     * @param group   the group's id, the part after {@code <type>:}
     * @param player  the player, online
     * @param profile the SkysongStats profile they play now, null when they play none
     * @return true when they are in it
     */
    boolean contains(@NotNull String group, @NotNull Player player, @Nullable String profile);

    /**
     * Group ids to suggest when a player types a group in {@code /snpc access}. A grant may
     * still name an id not listed here.
     *
     * @param player the player typing; suggest the groups they would want to name
     * @return ids, empty by default
     */
    default @NotNull Collection<String> suggestions(@NotNull Player player) {
        return List.of();
    }
}
