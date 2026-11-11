package org.skysongdev.skysongnpcs.items;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Whether regions let a player place an NPC somewhere. Without WorldGuard everywhere is
 * allowed; with it, {@link org.skysongdev.skysongnpcs.worldguard.WorldGuardPlaceRegions} checks
 * the {@code npc-place} flag.
 */
@FunctionalInterface
public interface PlaceRegions {
    PlaceRegions ANYWHERE = (player, location) -> true;

    boolean allows(Player player, Location location);
}
