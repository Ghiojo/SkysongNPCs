package org.skysongdev.skysongnpcs.items;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Finds where a new NPC can stand near a location, with the checks placing a blueprint makes:
 * two free blocks on solid ground, the per-chunk limit, and, when a player places it, the
 * regions (WorldGuard's {@code npc-place} flag). The owner's per-character limit is left to
 * blueprints, since plugins that make NPCs (Economy's slots) keep their own count.
 *
 * <p>Only loaded chunks are searched: the search never loads one.
 */
public final class SpotFinder {
    private final NpcRegistry registry;
    private final NpcSpawner spawner;
    private final PlaceRegions regions;
    private final Supplier<PlacingSettings> settings;

    public SpotFinder(NpcRegistry registry, NpcSpawner spawner, PlaceRegions regions,
                      Supplier<PlacingSettings> settings) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.spawner = Objects.requireNonNull(spawner, "spawner");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Whether an NPC's feet and head fit at {@code feet}: neither block is in the way. */
    public static boolean hasRoom(Block feet) {
        return !feet.getType().isCollidable() && !feet.getRelative(BlockFace.UP).getType().isCollidable();
    }

    /** Whether an NPC can stand at {@code feet}: room for it, on a solid block. */
    public static boolean canStand(Block feet) {
        return hasRoom(feet) && feet.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    /** How many Skysong NPCs stand in the chunk now. */
    public int managedIn(Chunk chunk) {
        return (int) spawner.spawnedIn(chunk).stream().filter(registry::isManaged).count();
    }

    /**
     * The closest spot to {@code near} where a new NPC can stand, no further than
     * {@code radius} blocks along each axis.
     *
     * @param placer the player it is placed for, whose regions are checked and who is not
     *               limited when they are staff; null for none (no region check)
     * @return the middle of the block its feet would be in, facing as {@code near} does; empty
     *         when there is no such spot
     * @throws IllegalArgumentException when {@code near} has no world or {@code radius} is
     *                                  negative
     */
    public Optional<Location> find(Location near, int radius, @Nullable Player placer) {
        Objects.requireNonNull(near, "near");
        World world = near.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("The location has no world");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("The radius cannot be negative: " + radius);
        }
        int reach = Math.min(radius, settings.get().maxSearchRadius());
        int perChunk = placer != null && placer.hasPermission(Permissions.ADMIN) ? 0 : settings.get().perChunk();
        Map<Long, Integer> chunkCounts = new HashMap<>();
        int x0 = near.getBlockX();
        int y0 = near.getBlockY();
        int z0 = near.getBlockZ();
        for (int[] offset : offsets(reach)) {
            int x = x0 + offset[0];
            int y = y0 + offset[1];
            int z = z0 + offset[2];
            if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block feet = world.getBlockAt(x, y, z);
            if (!canStand(feet)) {
                continue;
            }
            Location spot = new Location(world, x + 0.5, y, z + 0.5, near.getYaw(), near.getPitch());
            if (placer != null && !regions.allows(placer, spot)) {
                continue;
            }
            if (perChunk > 0) {
                Chunk chunk = feet.getChunk();
                int count = chunkCounts.computeIfAbsent(chunk.getChunkKey(), key -> managedIn(chunk));
                if (!PlacingSettings.roomFor(perChunk, count)) {
                    continue;
                }
            }
            return Optional.of(spot);
        }
        return Optional.empty();
    }

    /** Every offset in the cube, nearest first; at the same distance, the lower one first. */
    private static List<int[]> offsets(int reach) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    offsets.add(new int[]{dx, dy, dz});
                }
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2])
                .thenComparingInt(o -> o[1]));
        return offsets;
    }
}
