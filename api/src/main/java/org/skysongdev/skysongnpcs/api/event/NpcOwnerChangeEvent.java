package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

/**
 * A managed NPC got a new owner, through {@code /snpc owner} or the API. Fired after the
 * change is made, on the main thread.
 */
public final class NpcOwnerChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ManagedNpc npc;
    private final NpcOwner previousOwner;

    @ApiStatus.Internal
    public NpcOwnerChangeEvent(@NotNull ManagedNpc npc, @NotNull NpcOwner previousOwner) {
        this.npc = npc;
        this.previousOwner = previousOwner;
    }

    /**
     * @return the NPC, with its new owner
     */
    public @NotNull ManagedNpc getNpc() {
        return npc;
    }

    /**
     * @return who owned it before
     */
    public @NotNull NpcOwner getPreviousOwner() {
        return previousOwner;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
