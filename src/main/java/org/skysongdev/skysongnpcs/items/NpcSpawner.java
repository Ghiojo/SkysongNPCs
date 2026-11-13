package org.skysongdev.skysongnpcs.items;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Makes and finds Citizens NPCs in the world. {@link org.skysongdev.skysongnpcs.citizens.CitizensNpcSpawner}
 * is the real one; tests use their own, since Citizens is not on the test classpath.
 */
public interface NpcSpawner {

    /**
     * Creates an NPC in Citizens' main registry and spawns it.
     *
     * @param skin a player skin's name, used only for player NPCs; null for none
     * @return its Citizens UUID; empty when the spawn was refused (another plugin cancelled
     *         it), in which case nothing is left behind
     */
    Optional<UUID> spawn(EntityType type, String name, @Nullable String skin, Location location);

    /**
     * Removes an NPC from Citizens for good, as {@code /npc remove} does. On a server Citizens
     * fires its remove event before this returns, so the record and the stat sheet are gone too.
     *
     * @return false when Citizens has no such NPC
     */
    boolean destroy(UUID npc);

    /** The Citizens NPCs of the main registry that stand in this chunk now. */
    List<UUID> spawnedIn(Chunk chunk);

    // M18: pickup

    /**
     * Despawns an NPC and keeps it in Citizens with everything it has; Citizens does not spawn
     * it again by itself, not even after a restart.
     *
     * @return false when Citizens has no such NPC
     */
    boolean despawn(UUID npc);

    /**
     * Spawns a despawned NPC again.
     *
     * @return false when Citizens has no such NPC or the spawn was refused
     */
    boolean respawn(UUID npc, Location location);

    /** Where the NPC stands now; empty when it is not spawned. */
    Optional<Location> location(UUID npc);
}
