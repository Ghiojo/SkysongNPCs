package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.Optional;
import java.util.UUID;

/**
 * A player is about to place an NPC from a blueprint item, or to place a picked-up NPC
 * again from its NPC item (see {@link #getNpc()}). Fired on the main thread once
 * every SkysongNPCs check has passed (permission, space, regions, limits) and before the NPC
 * is made. Cancelling it places nothing, keeps the item and sends the player no message, so
 * the plugin that cancels says why.
 *
 * @since 0.6.0
 */
public final class NpcPlaceEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack item;
    private final String template;
    private final NpcOwner owner;
    private final Location location;
    private boolean cancelled;
    // M18
    private final @Nullable UUID npc;

    @ApiStatus.Internal
    public NpcPlaceEvent(@NotNull Player player, @NotNull ItemStack item, @NotNull String template,
                         @NotNull NpcOwner owner, @NotNull Location location) {
        this(player, item, template, owner, location, null);
    }

    @ApiStatus.Internal
    public NpcPlaceEvent(@NotNull Player player, @NotNull ItemStack item, @NotNull String template,
                         @NotNull NpcOwner owner, @NotNull Location location, @Nullable UUID npc) {
        this.npc = npc;
        this.player = player;
        this.item = item.clone();
        this.template = template;
        this.owner = owner;
        this.location = location.clone();
    }

    /**
     * @return the player placing it
     */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * @return a copy of the blueprint item; changing it changes nothing
     */
    public @NotNull ItemStack getItem() {
        return item.clone();
    }

    /**
     * @return the id of the template the NPC is made from; for an NPC placed again, the
     *         template it was made from, or an empty string when it has none
     */
    public @NotNull String getTemplate() {
        return template;
    }

    /**
     * @return the character that will own the NPC: the one the player is playing
     */
    public @NotNull NpcOwner getOwner() {
        return owner;
    }

    /**
     * @return where the NPC will stand, facing the player
     */
    public @NotNull Location getLocation() {
        return location.clone();
    }

    /**
     * @return the picked-up NPC an NPC item places again; empty when a blueprint makes a new one
     * @since 0.13.0
     */
    public @NotNull Optional<UUID> getNpc() {
        return Optional.ofNullable(npc);
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
