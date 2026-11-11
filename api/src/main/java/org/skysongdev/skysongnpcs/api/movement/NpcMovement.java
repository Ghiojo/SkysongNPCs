package org.skysongdev.skysongnpcs.api.movement;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Moves managed NPCs: teleports them, makes them follow someone, and makes them look at
 * nearby players. These calls do not check anyone's access or config.yml's movement limits;
 * that is up to the calling plugin (see {@link org.skysongdev.skysongnpcs.api.access.Access}).
 *
 * <p>Must be called on the server's main thread.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 *
 * @since 0.9.0
 */
@ApiStatus.NonExtendable
public interface NpcMovement {

    /**
     * Teleports an NPC that is out in the world.
     *
     * @param npc the NPC's Citizens UUID
     * @param to  where it goes
     * @return false, moving nothing, when the NPC is not managed, or is picked up or despawned
     */
    boolean teleport(@NotNull UUID npc, @NotNull Location to);

    /**
     * Makes an NPC follow an entity, keeping about {@code distance} blocks away. Replaces
     * whoever it followed before.
     *
     * @param npc      the NPC's Citizens UUID
     * @param target   who to follow
     * @param distance how close it stays, in blocks; at least 1
     * @return false, changing nothing, when the NPC is not managed
     * @throws IllegalArgumentException when the distance is below 1
     */
    boolean follow(@NotNull UUID npc, @NotNull Entity target, double distance);

    /**
     * Makes an NPC stop following.
     *
     * @param npc the NPC's Citizens UUID
     * @return false when the NPC is not managed
     */
    boolean stopFollowing(@NotNull UUID npc);

    /**
     * @param npc the NPC's Citizens UUID
     * @return who the NPC follows, empty when it follows nobody or is not managed
     */
    @NotNull Optional<UUID> following(@NotNull UUID npc);

    /**
     * Turns looking at nearby players on or off.
     *
     * @param npc   the NPC's Citizens UUID
     * @param on    true to look at players
     * @param range how far away, in blocks, players get looked at; at least 1, only used when on
     * @return false, changing nothing, when the NPC is not managed
     * @throws IllegalArgumentException when on and the range is below 1
     */
    boolean look(@NotNull UUID npc, boolean on, double range);

    /**
     * @param npc the NPC's Citizens UUID
     * @return whether the NPC looks at nearby players; false when it is not managed
     */
    boolean isLooking(@NotNull UUID npc);
}
