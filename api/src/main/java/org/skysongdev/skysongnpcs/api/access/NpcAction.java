package org.skysongdev.skysongnpcs.api.access;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Something a player may do with a managed NPC. The owner and staff may do all of them;
 * everyone else needs an {@link AccessGrant} that allows it.
 *
 * <p>An {@code -edit} action covers its {@code -view}: a grant of {@link #INVENTORY_EDIT}
 * also allows {@link #INVENTORY_VIEW}. See {@link #covers(NpcAction)}.
 */
public enum NpcAction {
    // Look at
    /** See the NPC's information: name, owner, state. */
    INFO,
    /** See the NPC's stat sheet. */
    STATS_VIEW,
    /** See the NPC's abilities. */
    ABILITIES_VIEW,
    /** See the NPC's wardrobe without changing it. */
    WARDROBE_VIEW,
    /** See the NPC's inventory without changing it. */
    INVENTORY_VIEW,
    /** See the NPC's equipment without changing it. */
    EQUIPMENT_VIEW,

    // Move
    /** Teleport the NPC to yourself. */
    TELEPORT,
    /** Make the NPC follow someone, or stop. */
    FOLLOW,
    /** Make the NPC look at nearby players, or stop. */
    LOOK,

    // Change
    /** Change the NPC's wardrobe. */
    WARDROBE_EDIT,
    /** Change the NPC's inventory. */
    INVENTORY_EDIT,
    /** Change the NPC's equipment. */
    EQUIPMENT_EDIT,
    /** Rename the NPC. */
    RENAME,
    /** Change the NPC's base skin. */
    SKIN,
    /** Change the nameplate, pose, sitting and glowing. */
    APPEARANCE,
    /** Make the NPC's choices: setup, stat points, proficiency picks. */
    CHOOSE,

    // Control
    /** Pick the NPC up into its item. */
    PICKUP,
    /** Change who may do what with the NPC: its grants and its policy. */
    MANAGE;

    /**
     * The action's id as players type it and config.yml writes it, such as {@code stats-view}.
     *
     * @return the id
     */
    public @NotNull String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Whether allowing this action also allows {@code other}: the same action, or the
     * {@code -view} of this {@code -edit}.
     *
     * @param other another action
     * @return true when this one covers it
     */
    public boolean covers(@NotNull NpcAction other) {
        return this == other || view() == other;
    }

    private NpcAction view() {
        return switch (this) {
            case WARDROBE_EDIT -> WARDROBE_VIEW;
            case INVENTORY_EDIT -> INVENTORY_VIEW;
            case EQUIPMENT_EDIT -> EQUIPMENT_VIEW;
            default -> null;
        };
    }

    /**
     * The action with this id, ignoring case.
     *
     * @param key an id such as {@code stats-view}
     * @return the action, empty when no action has that id
     */
    public static @NotNull Optional<NpcAction> fromKey(@NotNull String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (NpcAction action : values()) {
            if (action.key().equals(normalized)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
