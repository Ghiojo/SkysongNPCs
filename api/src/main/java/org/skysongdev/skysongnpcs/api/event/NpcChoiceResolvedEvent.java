package org.skysongdev.skysongnpcs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;

/**
 * A pending choice of an NPC was made: stat points spent, or another kind of choice. Fired
 * after the change is saved, on the main thread.
 *
 * @since 0.14.0
 */
public final class NpcChoiceResolvedEvent extends Event {
    /** The kind of stat points: the choice is a stat id, the amount the points spent. */
    public static final String STAT_POINTS = "stat-points";
    /**
     * M15: a proficiency pick; the choice is the proficiency id and the amount 1.
     *
     * @since 0.15.0
     */
    public static final String PROFICIENCY_PICKS = "proficiency-picks";
    /**
     * An ability choice; the choice is the ability id and the amount 1.
     *
     * @since 0.23.0
     */
    public static final String ABILITY_CHOICES = "ability-choices";

    private static final HandlerList HANDLERS = new HandlerList();

    private final ManagedNpc npc;
    private final Player player;
    private final String kind;
    private final String choice;
    private final int amount;

    @ApiStatus.Internal
    public NpcChoiceResolvedEvent(@NotNull ManagedNpc npc, @Nullable Player player, @NotNull String kind,
                                  @NotNull String choice, int amount) {
        this.npc = npc;
        this.player = player;
        this.kind = kind;
        this.choice = choice;
        this.amount = amount;
    }

    /** @return the NPC */
    public @NotNull ManagedNpc getNpc() {
        return npc;
    }

    /** @return who made the choice; null when a plugin did through the API */
    public @Nullable Player getPlayer() {
        return player;
    }

    /** @return what kind of choice, such as {@link #STAT_POINTS} */
    public @NotNull String getKind() {
        return kind;
    }

    /** @return what was chosen, such as a stat id */
    public @NotNull String getChoice() {
        return choice;
    }

    /** @return how much, such as the points spent */
    public int getAmount() {
        return amount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
