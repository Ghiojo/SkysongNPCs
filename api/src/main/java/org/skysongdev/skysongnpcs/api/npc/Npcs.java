package org.skysongdev.skysongnpcs.api.npc;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The managed NPCs: Citizens NPCs SkysongNPCs keeps a record for. Plain Citizens NPCs are
 * left alone and never show up here.
 *
 * <p>A record follows its Citizens NPC: removing the NPC with {@code /npc remove} (or
 * {@code NPC#destroy()}) deletes the record and fires
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
     * template. This is what {@code /snpc adopt} does.
     *
     * @param npc   the Citizens UUID of an NPC in Citizens' main registry
     * @param owner its owner
     * @return the new record
     * @throws IllegalArgumentException when Citizens has no such NPC
     * @throws IllegalStateException    when the NPC is managed already
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
     * Gives an NPC to another owner. Fires
     * {@link org.skysongdev.skysongnpcs.api.event.NpcOwnerChangeEvent} when the owner changes.
     *
     * @param npc   the NPC's Citizens UUID
     * @param owner the new owner
     * @return the NPC with its new owner
     * @throws IllegalArgumentException when the NPC is not managed
     */
    @NotNull ManagedNpc setOwner(@NotNull UUID npc, @NotNull NpcOwner owner);
}
