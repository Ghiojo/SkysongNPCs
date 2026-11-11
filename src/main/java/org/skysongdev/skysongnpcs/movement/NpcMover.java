package org.skysongdev.skysongnpcs.movement;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Optional;
import java.util.UUID;

/**
 * What movement asks Citizens to do. {@link org.skysongdev.skysongnpcs.citizens.CitizensMover}
 * is the real one; tests use their own, since Citizens is not on the test classpath.
 */
public interface NpcMover {

    /** Where the NPC stands; empty when it is not spawned. */
    Optional<Location> location(UUID npc);

    /** Teleports a spawned NPC; false when it is not spawned. */
    boolean teleport(UUID npc, Location to);

    /** Follows the target at about {@code distance} blocks; a null target stops following. */
    void follow(UUID npc, Entity target, double distance);

    /** Who the NPC follows. */
    Optional<UUID> following(UUID npc);

    /** Turns Citizens' look-close on or off, with its range in blocks. */
    void look(UUID npc, boolean on, double range);

    boolean looking(UUID npc);
}
