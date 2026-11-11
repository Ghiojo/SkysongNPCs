package org.skysongdev.skysongnpcs;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.skysongdev.skysongnpcs.movement.NpcMover;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Citizens' movement for tests: where each spawned NPC stands, who it follows, whether it looks. */
public final class FakeMover implements NpcMover {
    private final Map<UUID, Location> locations = new HashMap<>();
    private final Map<UUID, UUID> following = new HashMap<>();
    private final Map<UUID, Double> distances = new HashMap<>();
    private final Map<UUID, Double> lookRanges = new HashMap<>();

    /** Spawns the NPC at a location; null despawns it. */
    public void place(UUID npc, Location at) {
        if (at == null) {
            locations.remove(npc);
        } else {
            locations.put(npc, at.clone());
        }
    }

    public double followDistance(UUID npc) {
        return distances.getOrDefault(npc, 0.0);
    }

    public double lookRange(UUID npc) {
        return lookRanges.getOrDefault(npc, 0.0);
    }

    @Override
    public Optional<Location> location(UUID npc) {
        return Optional.ofNullable(locations.get(npc)).map(Location::clone);
    }

    @Override
    public boolean teleport(UUID npc, Location to) {
        if (!locations.containsKey(npc)) {
            return false;
        }
        locations.put(npc, to.clone());
        return true;
    }

    @Override
    public void follow(UUID npc, Entity target, double distance) {
        if (target == null) {
            following.remove(npc);
        } else {
            following.put(npc, target.getUniqueId());
            distances.put(npc, distance);
        }
    }

    @Override
    public Optional<UUID> following(UUID npc) {
        return Optional.ofNullable(following.get(npc));
    }

    @Override
    public void look(UUID npc, boolean on, double range) {
        if (on) {
            lookRanges.put(npc, range);
        } else {
            lookRanges.remove(npc);
        }
    }

    @Override
    public boolean looking(UUID npc) {
        return lookRanges.containsKey(npc);
    }
}
