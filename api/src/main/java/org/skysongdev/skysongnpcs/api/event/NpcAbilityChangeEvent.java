package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A managed NPC gained or lost an ability, through {@code /snpc abilities}, the API, or a
 * reload that found the ability gone from abilities.yml. Fired after the change, on the main
 * thread. Not fired when abilities go with a deleted NPC or come with a copied one.
 *
 * @since 0.16.0
 */
public final class NpcAbilityChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID npc;
    private final String ability;
    private final Change change;

    @ApiStatus.Internal
    public NpcAbilityChangeEvent(@NotNull UUID npc, @NotNull String ability, @NotNull Change change) {
        this.npc = npc;
        this.ability = ability;
        this.change = change;
    }

    /**
     * @return the NPC's Citizens UUID
     */
    public @NotNull UUID getNpc() {
        return npc;
    }

    /**
     * @return the ability's id
     */
    public @NotNull String getAbility() {
        return ability;
    }

    /**
     * @return what happened
     */
    public @NotNull Change getChange() {
        return change;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    /** What happened to the ability. */
    public enum Change {
        /** It was granted. */
        GRANTED,
        /** It was revoked. */
        REVOKED,
        /** {@code /snpc reload} found it gone from abilities.yml, so every NPC lost it. */
        REMOVED_FROM_FILE
    }
}
