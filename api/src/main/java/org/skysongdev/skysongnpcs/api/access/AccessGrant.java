package org.skysongdev.skysongnpcs.api.access;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Lets one group do some actions with one NPC. An NPC has at most one grant per group and
 * source; a player may do an action when any grant whose group they are in allows it.
 *
 * @param group   who it lets in
 * @param source  who made it, and so who may change it
 * @param actions what they may do; never empty, unmodifiable
 */
public record AccessGrant(@NotNull AccessGroup group, @NotNull GrantSource source, @NotNull Set<NpcAction> actions) {

    /**
     * @throws IllegalArgumentException when there are no actions
     */
    public AccessGrant {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(actions, "actions");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("A grant allows at least one action");
        }
        actions = Collections.unmodifiableSet(EnumSet.copyOf(actions));
    }

    /**
     * Whether this grant allows an action, directly or through an {@code -edit} that covers it.
     *
     * @param action the action
     * @return true when it is allowed
     */
    public boolean allows(@NotNull NpcAction action) {
        for (NpcAction granted : actions) {
            if (granted.covers(action)) {
                return true;
            }
        }
        return false;
    }
}
