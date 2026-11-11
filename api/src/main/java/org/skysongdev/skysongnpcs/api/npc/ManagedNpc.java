package org.skysongdev.skysongnpcs.api.npc;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * A Citizens NPC that SkysongNPCs manages, as it was when you got it. It does not change
 * afterwards: ask {@link Npcs#get(UUID)} again to see later changes.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface ManagedNpc {

    /**
     * The NPC's Citizens UUID ({@code NPC#getUniqueId()}), the key SkysongNPCs, SkysongStats
     * and SkysongWardrobe all use for it.
     *
     * @return the UUID
     */
    @NotNull UUID id();

    /**
     * @return who owns the NPC
     */
    @NotNull NpcOwner owner();

    /**
     * @return where the NPC is in its life
     */
    @NotNull NpcState state();

    /**
     * The template the NPC was made from, such as {@code guard_dog}.
     *
     * @return the template's id, empty for NPCs adopted by staff
     */
    @NotNull Optional<String> template();
}
