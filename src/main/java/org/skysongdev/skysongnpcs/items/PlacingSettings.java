package org.skysongdev.skysongnpcs.items;

import org.bukkit.configuration.ConfigurationSection;

/**
 * config.yml's {@code placing:} section.
 *
 * @param perCharacter how many NPCs one character may have out in the world (not picked up); 0 for no limit
 * @param perChunk     how many Skysong NPCs one chunk may hold; 0 for no limit
 * @param maxSearchRadius the furthest the API's free-spot search looks, in blocks along each axis
 */
public record PlacingSettings(int perCharacter, int perChunk, int maxSearchRadius) {

    /** The free-spot search's furthest reach when config.yml does not set one. */
    public static final int DEFAULT_SEARCH_RADIUS = 16;

    /** Reads the section; a missing or negative limit means no limit. */
    public static PlacingSettings read(ConfigurationSection config) {
        return new PlacingSettings(
                Math.max(0, config.getInt("placing.limits.per-character", 0)),
                Math.max(0, config.getInt("placing.limits.per-chunk", 0)),
                Math.max(0, config.getInt("placing.find-spot.max-radius", DEFAULT_SEARCH_RADIUS)));
    }

    /** Whether one more fits under {@code limit} when there are {@code count} already. */
    public static boolean roomFor(int limit, int count) {
        return limit <= 0 || count < limit;
    }
}
