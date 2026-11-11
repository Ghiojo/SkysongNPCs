package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;

/**
 * A managed NPC's record was deleted. Fired after it is gone, on the main thread.
 */
public final class NpcDeleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ManagedNpc npc;
    private final Cause cause;

    @ApiStatus.Internal
    public NpcDeleteEvent(@NotNull ManagedNpc npc, @NotNull Cause cause) {
        this.npc = npc;
        this.cause = cause;
    }

    /**
     * @return the NPC as it was just before its record was deleted
     */
    public @NotNull ManagedNpc getNpc() {
        return npc;
    }

    /**
     * @return why the record was deleted
     */
    public @NotNull Cause getCause() {
        return cause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    /** Why a record was deleted. */
    public enum Cause {
        /** The Citizens NPC itself was removed ({@code /npc remove} or {@code NPC#destroy()}). */
        REMOVED,
        /** Staff or a plugin released it ({@code /snpc release}); the Citizens NPC stays as a plain NPC. */
        RELEASED
    }
}
