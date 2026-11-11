package org.skysongdev.skysongnpcs.movement;

import org.bukkit.configuration.ConfigurationSection;

/**
 * config.yml's {@code movement:} section. Staff are not held to the maximums.
 *
 * @param teleportMaxDistance  how far, in blocks, an NPC may be brought from with tphere; 0 for any distance
 * @param teleportOtherWorlds  whether tphere may bring an NPC from another world
 * @param followDistance       how close a following NPC stays when no distance is given
 * @param followMaxDistance    the largest follow distance players may ask for
 * @param followOthers         whether players may make an NPC follow someone other than themselves
 * @param lookRange            how far an NPC looks at players when no range is given
 * @param lookMaxRange         the largest look range players may ask for
 */
public record MovementSettings(double teleportMaxDistance, boolean teleportOtherWorlds,
                               double followDistance, double followMaxDistance, boolean followOthers,
                               double lookRange, double lookMaxRange) {

    public static MovementSettings read(ConfigurationSection config) {
        double followMax = Math.max(1, config.getDouble("movement.follow.max-distance", 10));
        double lookMax = Math.max(1, config.getDouble("movement.look.max-range", 16));
        return new MovementSettings(
                Math.max(0, config.getDouble("movement.teleport.max-distance", 0)),
                config.getBoolean("movement.teleport.other-worlds", true),
                clamp(config.getDouble("movement.follow.default-distance", 3), followMax),
                followMax,
                config.getBoolean("movement.follow.others", true),
                clamp(config.getDouble("movement.look.default-range", 5), lookMax),
                lookMax);
    }

    private static double clamp(double value, double max) {
        return Math.min(Math.max(1, value), max);
    }
}
