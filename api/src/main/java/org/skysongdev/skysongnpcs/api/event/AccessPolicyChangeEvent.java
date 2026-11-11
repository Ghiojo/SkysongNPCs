package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;

import java.util.Set;

/**
 * One grant of an access policy was made, changed or deleted, through {@code /snpc policy} or
 * the API, changing access to every NPC that follows it. Fired after the change, on the main
 * thread. Not fired when a whole policy is deleted.
 */
public final class AccessPolicyChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String policy;
    private final AccessGroup group;
    private final Set<NpcAction> previous;
    private final Set<NpcAction> current;

    @ApiStatus.Internal
    public AccessPolicyChangeEvent(@NotNull String policy, @NotNull AccessGroup group,
                                   @NotNull Set<NpcAction> previous, @NotNull Set<NpcAction> current) {
        this.policy = policy;
        this.group = group;
        this.previous = Set.copyOf(previous);
        this.current = Set.copyOf(current);
    }

    /**
     * @return the policy's id
     */
    public @NotNull String getPolicy() {
        return policy;
    }

    /**
     * @return the group the grant lets in
     */
    public @NotNull AccessGroup getGroup() {
        return group;
    }

    /**
     * @return what the grant allowed before; empty when it is new
     */
    public @NotNull Set<NpcAction> getPrevious() {
        return previous;
    }

    /**
     * @return what it allows now; empty when it was deleted
     */
    public @NotNull Set<NpcAction> getCurrent() {
        return current;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
