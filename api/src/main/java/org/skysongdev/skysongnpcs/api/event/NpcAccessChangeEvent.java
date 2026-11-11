package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;

import java.util.Set;
import java.util.UUID;

/**
 * One grant of a managed NPC was made, changed or deleted, through {@code /snpc access} or the
 * API. Fired after the change, on the main thread. Not fired when grants go with a deleted NPC
 * or come with a copied one.
 */
public final class NpcAccessChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID npc;
    private final AccessGroup group;
    private final GrantSource source;
    private final Set<NpcAction> previous;
    private final Set<NpcAction> current;

    @ApiStatus.Internal
    public NpcAccessChangeEvent(@NotNull UUID npc, @NotNull AccessGroup group, @NotNull GrantSource source,
                                @NotNull Set<NpcAction> previous, @NotNull Set<NpcAction> current) {
        this.npc = npc;
        this.group = group;
        this.source = source;
        this.previous = Set.copyOf(previous);
        this.current = Set.copyOf(current);
    }

    /**
     * @return the NPC's Citizens UUID
     */
    public @NotNull UUID getNpc() {
        return npc;
    }

    /**
     * @return who made the grant
     */
    public @NotNull GrantSource getSource() {
        return source;
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
