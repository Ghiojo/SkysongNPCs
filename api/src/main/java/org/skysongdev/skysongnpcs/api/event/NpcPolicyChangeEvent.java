package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * A managed NPC started following an access policy, switched to another, or stopped following
 * one, through {@code /snpc policy}, the API, or its policy being deleted. Fired after the
 * change, on the main thread. Not fired when the NPC is deleted or copied.
 */
public final class NpcPolicyChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID npc;
    private final String previous;
    private final String current;

    @ApiStatus.Internal
    public NpcPolicyChangeEvent(@NotNull UUID npc, String previous, String current) {
        this.npc = npc;
        this.previous = previous;
        this.current = current;
    }

    /**
     * @return the NPC's Citizens UUID
     */
    public @NotNull UUID getNpc() {
        return npc;
    }

    /**
     * @return the id of the policy it followed before, empty when none
     */
    public @NotNull Optional<String> getPrevious() {
        return Optional.ofNullable(previous);
    }

    /**
     * @return the id of the policy it follows now, empty when none
     */
    public @NotNull Optional<String> getCurrent() {
        return Optional.ofNullable(current);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
