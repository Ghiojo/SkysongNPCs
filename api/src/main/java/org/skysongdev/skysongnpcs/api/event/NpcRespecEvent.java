package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A managed NPC was respecced: its spent choices (stat points, proficiency picks) were given
 * back, either every one of them or only those one source's grants paid for. Fired after the
 * change, on the main thread, for every respec: staff's {@code /snpc respec} and the API's
 * {@link org.skysongdev.skysongnpcs.api.choices.NpcChoices#respec respec}. It fires even when
 * there was nothing to give back, so a plugin can check its own choices again whenever one
 * happens.
 *
 * @since 0.21.0
 */
public final class NpcRespecEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID npc;
    private final @Nullable String source;
    private final int givenBack;

    @ApiStatus.Internal
    public NpcRespecEvent(@NotNull UUID npc, @Nullable String source, int givenBack) {
        this.npc = npc;
        this.source = source;
        this.givenBack = givenBack;
    }

    /**
     * @return the NPC's Citizens UUID
     */
    public @NotNull UUID getNpc() {
        return npc;
    }

    /**
     * @return the one source whose choices were given back; null when every source's were
     */
    public @Nullable String getSource() {
        return source;
    }

    /**
     * @return whether every source's choices were given back
     */
    public boolean isEverySource() {
        return source == null;
    }

    /**
     * @return how many choices were given back (stat points count one each); may be 0
     */
    public int getGivenBack() {
        return givenBack;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
