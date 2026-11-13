package org.skysongdev.skysongnpcs;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.items.NpcSpawner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Citizens' NPC creation for tests: remembers what was spawned where. */
public final class FakeNpcSpawner implements NpcSpawner {
    /** One spawned NPC. */
    public record Spawned(EntityType type, String name, @Nullable String skin, Location location) {
    }

    private final Map<UUID, Spawned> spawned = new LinkedHashMap<>();
    private boolean refuse;
    // M18: despawned NPCs, still in Citizens.
    private final Map<UUID, Spawned> despawned = new LinkedHashMap<>();

    /** Every spawn from now on is refused, as when another plugin cancels it. */
    public void refuseSpawns(boolean refuse) {
        this.refuse = refuse;
    }

    public Map<UUID, Spawned> spawned() {
        return spawned;
    }

    @Override
    public Optional<UUID> spawn(EntityType type, String name, @Nullable String skin, Location location) {
        if (refuse) {
            return Optional.empty();
        }
        UUID npc = UUID.randomUUID();
        spawned.put(npc, new Spawned(type, name, skin, location.clone()));
        return Optional.of(npc);
    }

    // Citizens' remove event, which tests cannot fire: OfflineSkysongNPCs passes what the
    // plugin's listeners do, and the NPCs only FakeNpcLookup knows (adopted ones).
    private Consumer<UUID> removeEvent = npc -> {
    };
    private Predicate<UUID> removeElsewhere = npc -> false;

    /**
     * Makes {@link #destroy} act like Citizens: an NPC it knows is also removed from
     * {@code elsewhere}, and {@code event} runs for it as Citizens' remove event would.
     */
    public void actLikeCitizens(Predicate<UUID> elsewhere, Consumer<UUID> event) {
        this.removeElsewhere = elsewhere;
        this.removeEvent = event;
    }

    @Override
    public boolean destroy(UUID npc) {
        boolean known = spawned.remove(npc) != null;
        known |= despawned.remove(npc) != null;
        known |= removeElsewhere.test(npc);
        if (known) {
            removeEvent.accept(npc);
        }
        return known;
    }

    @Override
    public List<UUID> spawnedIn(Chunk chunk) {
        return spawned.entrySet().stream()
                .filter(entry -> {
                    Location at = entry.getValue().location();
                    return at.getWorld().equals(chunk.getWorld())
                            && at.getBlockX() >> 4 == chunk.getX() && at.getBlockZ() >> 4 == chunk.getZ();
                })
                .map(Map.Entry::getKey)
                .toList();
    }

    // M18: pickup

    /** NPCs picked up: kept, but not in the world. */
    public Map<UUID, Spawned> despawned() {
        return despawned;
    }

    @Override
    public boolean despawn(UUID npc) {
        Spawned found = spawned.remove(npc);
        if (found != null) {
            despawned.put(npc, found);
        }
        return found != null || despawned.containsKey(npc);
    }

    @Override
    public boolean respawn(UUID npc, Location location) {
        Spawned found = despawned.containsKey(npc) ? despawned.get(npc) : spawned.get(npc);
        if (found == null || refuse) {
            return false;
        }
        despawned.remove(npc);
        spawned.put(npc, new Spawned(found.type(), found.name(), found.skin(), location.clone()));
        return true;
    }

    @Override
    public Optional<Location> location(UUID npc) {
        return Optional.ofNullable(spawned.get(npc)).map(found -> found.location().clone());
    }
}
