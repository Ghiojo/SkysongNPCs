package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;

/**
 * A player is about to pick an NPC up into its item, from the NPC menu or {@code /snpc pickup}.
 * Fired on the main thread once SkysongNPCs has checked the player may, and before the NPC is
 * despawned. Cancelling it keeps the NPC where it is and sends the player no message, so the
 * plugin that cancels says why. Pickups through {@link org.skysongdev.skysongnpcs.api.items.NpcItems#pickUp}
 * do not fire it: the plugin calling it decides.
 *
 * @since 0.13.0
 */
public final class NpcPickupEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ManagedNpc npc;
    private boolean cancelled;

    @ApiStatus.Internal
    public NpcPickupEvent(@NotNull Player player, @NotNull ManagedNpc npc) {
        this.player = player;
        this.npc = npc;
    }

    /**
     * @return the player picking it up, who gets the item
     */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * @return the NPC, as it is before the pickup
     */
    public @NotNull ManagedNpc getNpc() {
        return npc;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
