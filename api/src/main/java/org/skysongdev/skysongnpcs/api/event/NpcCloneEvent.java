package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;

/**
 * A managed NPC was copied in Citizens ({@code /npc copy} or {@code NPC#copy()}), and the
 * copy got its own record with the same owner, state and template. Fired after the copy's
 * record is saved, on the main thread. Nothing is shared between the two afterwards.
 */
public final class NpcCloneEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ManagedNpc original;
    private final ManagedNpc copy;

    @ApiStatus.Internal
    public NpcCloneEvent(@NotNull ManagedNpc original, @NotNull ManagedNpc copy) {
        this.original = original;
        this.copy = copy;
    }

    /**
     * @return the NPC that was copied
     */
    public @NotNull ManagedNpc getOriginal() {
        return original;
    }

    /**
     * @return the new NPC
     */
    public @NotNull ManagedNpc getCopy() {
        return copy;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
