package org.skysongdev.skysongnpcs.movement;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.movement.NpcMovement;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Moves managed NPCs for the commands, the menu's Movement button and the API. The player
 * methods hold players to config.yml's movement limits (staff excepted); who may use them at
 * all is the caller's check, through the NPC's access.
 */
public final class MovementService implements NpcMovement {
    /** What a player's move came to. */
    public enum Result {
        DONE,
        /** The NPC is picked up or despawned. */
        NOT_PLACED,
        /** tphere from another world, which config.yml forbids. */
        OTHER_WORLD,
        /** tphere from further than config.yml allows. */
        TOO_FAR,
        /** A follow distance or look range above config.yml's maximum. */
        ABOVE_MAX,
        /** Following someone else, which config.yml forbids. */
        NOT_OTHERS
    }

    private final NpcRegistry registry;
    private final NpcMover mover;
    private final Supplier<MovementSettings> settings;

    public MovementService(NpcRegistry registry, NpcMover mover, Supplier<MovementSettings> settings) {
        this.registry = registry;
        this.mover = mover;
        this.settings = settings;
    }

    public MovementSettings settings() {
        return settings.get();
    }

    /** Brings the NPC to the player. */
    public Result teleportHere(Player player, NpcRecord npc, boolean staff) {
        Optional<Location> from = mover.location(npc.id());
        if (npc.state() == NpcState.STORED || from.isEmpty()) {
            return Result.NOT_PLACED;
        }
        Location to = player.getLocation();
        MovementSettings limits = settings.get();
        if (!staff) {
            boolean sameWorld = Objects.equals(from.get().getWorld(), to.getWorld());
            if (!sameWorld && !limits.teleportOtherWorlds()) {
                return Result.OTHER_WORLD;
            }
            if (sameWorld && limits.teleportMaxDistance() > 0 && from.get().distance(to) > limits.teleportMaxDistance()) {
                return Result.TOO_FAR;
            }
        }
        return mover.teleport(npc.id(), to) ? Result.DONE : Result.NOT_PLACED;
    }

    /** Makes the NPC follow {@code target}, the player themself unless config.yml allows others. */
    public Result follow(Player player, NpcRecord npc, Player target, double distance, boolean staff) {
        MovementSettings limits = settings.get();
        if (!staff) {
            if (!target.equals(player) && !limits.followOthers()) {
                return Result.NOT_OTHERS;
            }
            if (distance > limits.followMaxDistance()) {
                return Result.ABOVE_MAX;
            }
        }
        mover.follow(npc.id(), target, Math.max(1, distance));
        return Result.DONE;
    }

    /** Turns looking at players on, with a range, or off. */
    public Result look(NpcRecord npc, boolean on, double range, boolean staff) {
        if (on && !staff && range > settings.get().lookMaxRange()) {
            return Result.ABOVE_MAX;
        }
        mover.look(npc.id(), on, Math.max(1, range));
        return Result.DONE;
    }

    // The API: no limits and no access checks, only managed NPCs.

    @Override
    public boolean teleport(@NotNull UUID npc, @NotNull Location to) {
        Objects.requireNonNull(to, "to");
        Optional<NpcRecord> record = registry.get(Objects.requireNonNull(npc, "npc"));
        return record.isPresent() && record.get().state() != NpcState.STORED && mover.teleport(npc, to);
    }

    @Override
    public boolean follow(@NotNull UUID npc, @NotNull Entity target, double distance) {
        Objects.requireNonNull(target, "target");
        if (distance < 1) {
            throw new IllegalArgumentException("The follow distance must be at least 1, not " + distance);
        }
        if (registry.get(Objects.requireNonNull(npc, "npc")).isEmpty()) {
            return false;
        }
        mover.follow(npc, target, distance);
        return true;
    }

    @Override
    public boolean stopFollowing(@NotNull UUID npc) {
        if (registry.get(Objects.requireNonNull(npc, "npc")).isEmpty()) {
            return false;
        }
        mover.follow(npc, null, 0);
        return true;
    }

    @Override
    public @NotNull Optional<UUID> following(@NotNull UUID npc) {
        return registry.get(Objects.requireNonNull(npc, "npc")).isEmpty() ? Optional.empty() : mover.following(npc);
    }

    @Override
    public boolean look(@NotNull UUID npc, boolean on, double range) {
        if (on && range < 1) {
            throw new IllegalArgumentException("The look range must be at least 1, not " + range);
        }
        if (registry.get(Objects.requireNonNull(npc, "npc")).isEmpty()) {
            return false;
        }
        mover.look(npc, on, range);
        return true;
    }

    @Override
    public boolean isLooking(@NotNull UUID npc) {
        return registry.get(Objects.requireNonNull(npc, "npc")).isPresent() && mover.looking(npc);
    }
}
