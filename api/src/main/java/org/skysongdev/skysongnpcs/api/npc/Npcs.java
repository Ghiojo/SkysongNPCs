package org.skysongdev.skysongnpcs.api.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The managed NPCs: Citizens NPCs SkysongNPCs keeps a record for. Plain Citizens NPCs are
 * left alone and never show up here.
 *
 * <p>A record follows its Citizens NPC: removing the NPC with {@code /npc remove},
 * {@code NPC#destroy()} or {@link #remove} deletes the record and fires
 * {@link org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent}, and copying it with
 * {@code /npc copy} (or {@code NPC#copy()}) gives the copy its own record and fires
 * {@link org.skysongdev.skysongnpcs.api.event.NpcCloneEvent}.
 *
 * <p>Must be called on the server's main thread, and throws {@link IllegalStateException} otherwise.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface Npcs {

    /**
     * Whether SkysongNPCs manages this NPC.
     *
     * @param npc the NPC's Citizens UUID
     * @return true when it has a record
     */
    boolean isManaged(@NotNull UUID npc);

    /**
     * A managed NPC.
     *
     * @param npc the NPC's Citizens UUID
     * @return the NPC, empty when it is not managed
     */
    @NotNull Optional<ManagedNpc> get(@NotNull UUID npc);

    /**
     * @return every managed NPC, an unmodifiable list in the order they were added
     */
    @NotNull List<ManagedNpc> all();

    /**
     * The NPCs one owner has.
     *
     * @param owner a character, or {@link NpcOwner#server()}
     * @return the NPCs, an unmodifiable list, empty when it has none
     */
    @NotNull List<ManagedNpc> ownedBy(@NotNull NpcOwner owner);

    /**
     * The NPCs any character of one player owns.
     *
     * @param player the player's UUID
     * @return the NPCs, an unmodifiable list, empty when they have none
     */
    @NotNull List<ManagedNpc> ownedByPlayer(@NotNull UUID player);

    /**
     * Starts managing an existing Citizens NPC, in state {@link NpcState#PLACED} with no
     * template. This is what {@code /snpc adopt} does. The NPC gets its stat sheet in the same
     * step (every managed NPC has one), starting from config.yml's {@code sheets.adopt-template}
     * or with every stat at its stats.yml start value (since 0.17.0; before, adopted NPCs had
     * no sheet).
     *
     * @param npc   the Citizens UUID of an NPC in Citizens' main registry
     * @param owner its owner
     * @return the new record
     * @throws IllegalArgumentException when Citizens has no such NPC
     * @throws IllegalStateException    when the NPC is managed already, or SkysongStats would not
     *                                  make its stat sheet; then it is not managed
     */
    @NotNull ManagedNpc adopt(@NotNull UUID npc, @NotNull NpcOwner owner);

    /**
     * Stops managing an NPC: its record and everything SkysongNPCs kept for it are deleted,
     * and it stays in the world as a plain Citizens NPC. Fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent}. This is what
     * {@code /snpc release} does.
     *
     * @param npc the NPC's Citizens UUID
     * @return true when it was managed, false when there was nothing to release
     */
    boolean release(@NotNull UUID npc);

    /**
     * Removes a managed NPC for good, the same as {@code /npc remove}: the Citizens NPC is
     * destroyed, its record and everything SkysongNPCs kept for it are deleted, and its
     * SkysongStats character (its stat sheet) is deleted. Fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent} with cause
     * {@link org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent.Cause#REMOVED REMOVED}, as any
     * removal does.
     *
     * <p>It is synchronous: everything above is done when it returns. An NPC that was picked up
     * into an item is removed too, and its item no longer places it. When Citizens has lost the
     * NPC already, the record and sheet are still deleted.
     *
     * <p>Plain Citizens NPCs are never removed: use {@link #isManaged} first if that matters.
     *
     * @param npc the NPC's Citizens UUID
     * @return true when it was managed and is now removed, false when it is not managed; then
     *         nothing happens
     * @since 0.18.0
     */
    boolean remove(@NotNull UUID npc);

    /**
     * Gives an NPC to another owner. Fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcOwnerChangeEvent} when the owner changes.
     *
     * @param npc   the NPC's Citizens UUID
     * @param owner the new owner
     * @return the NPC with its new owner
     * @throws IllegalArgumentException when the NPC is not managed
     */
    @NotNull ManagedNpc setOwner(@NotNull UUID npc, @NotNull NpcOwner owner);

    // M17

    /**
     * Makes a new Citizens NPC from a template and spawns it, managed from the start in state
     * {@link NpcState#SETUP}, with a stat sheet when the template has one. This is what placing a
     * blueprint item does, without the player's checks (space, regions, limits) and without
     * {@link org.skysongdev.skysongnpcs.api.event.NpcPlaceEvent}. To get the space, region and
     * chunk checks, ask {@link #findSpot(Location, int)} for the location first.
     *
     * @param template the template's id, as in templates/&lt;id&gt;.yml
     * @param name     the NPC's name, or null for the template's
     * @param owner    its owner
     * @param location where it stands
     * @return the new NPC
     * @throws IllegalArgumentException when there is no such template
     * @throws IllegalStateException    when Citizens could not spawn it there (another plugin
     *                                  refused the spawn), or its stat sheet could not be made;
     *                                  nothing is left behind
     * @since 0.6.0
     */
    @NotNull ManagedNpc create(@NotNull String template, @Nullable String name, @NotNull NpcOwner owner,
                               @NotNull Location location);

    // Free spots

    /**
     * The closest spot to {@code near} where a new NPC can stand, for {@link #create}: two free
     * blocks (feet and head) on solid ground, in a chunk under config.yml's
     * {@code placing.limits.per-chunk}. The owner's per-character limit is not checked, since
     * that one is for blueprint items.
     *
     * <p>It looks no further than {@code radius} blocks along each axis, and never further than
     * config.yml's {@code placing.find-spot.max-radius} (16 by default). Only loaded chunks are
     * searched. Region flags are not checked: use {@link #findSpot(Location, int, Player)} when a
     * player is placing the NPC.
     *
     * @param near   where to look around
     * @param radius how far to look, in blocks; 0 checks {@code near}'s own block only
     * @return the middle of the block the NPC's feet would be in, facing as {@code near} does;
     *         empty when there is no free spot
     * @throws IllegalArgumentException when {@code near} has no world or {@code radius} is negative
     * @since 0.19.0
     */
    @NotNull Optional<Location> findSpot(@NotNull Location near, int radius);

    /**
     * {@link #findSpot(Location, int)} for an NPC a player is placing: also checks that
     * regions let that player place NPCs there (WorldGuard's {@code npc-place} flag, the same as
     * blueprint items), and staff ({@code skysongnpcs.admin}) are not held to the per-chunk
     * limit.
     *
     * @param near   where to look around
     * @param radius how far to look, in blocks
     * @param placer the player the NPC is placed for
     * @return the spot, or empty when there is none
     * @throws IllegalArgumentException when {@code near} has no world or {@code radius} is negative
     * @since 0.19.0
     */
    @NotNull Optional<Location> findSpot(@NotNull Location near, int radius, @NotNull Player placer);
}
