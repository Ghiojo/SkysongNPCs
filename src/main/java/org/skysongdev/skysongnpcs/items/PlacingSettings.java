package org.skysongdev.skysongnpcs.items;

import org.bukkit.configuration.ConfigurationSection;

/**
 * config.yml's {@code placing:} section.
 *
 * @param perCharacter how many NPCs one character may have out in the world (not picked up); 0 for no limit
 * @param perChunk     how many Skysong NPCs one chunk may hold; 0 for no limit
 */
public record PlacingSettings(int perCharacter, int perChunk) {

    /** Reads the section; a missing or negative limit means no limit. */
    public static PlacingSettings read(ConfigurationSection config) {
        return new PlacingSettings(
                Math.max(0, config.getInt("placing.limits.per-character", 0)),
                Math.max(0, config.getInt("placing.limits.per-chunk", 0)));
    }

    /** Whether one more fits under {@code limit} when there are {@code count} already. */
    public static boolean roomFor(int limit, int count) {
        return limit <= 0 || count < limit;
    }
}
