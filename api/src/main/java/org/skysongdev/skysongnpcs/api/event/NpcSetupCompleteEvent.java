package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;

/**
 * An NPC finished its first setup and is now PLACED. Fired after the change is saved, on the
 * main thread.
 *
 * @since 0.14.0
 */
public final class NpcSetupCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ManagedNpc npc;
    private final Player player;

    @ApiStatus.Internal
    public NpcSetupCompleteEvent(@NotNull ManagedNpc npc, @Nullable Player player) {
        this.npc = npc;
        this.player = player;
    }

    /** @return the NPC, as it is now */
    public @NotNull ManagedNpc getNpc() {
        return npc;
    }

    /** @return who finished the setup; null when it finished without a player */
    public @Nullable Player getPlayer() {
        return player;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
