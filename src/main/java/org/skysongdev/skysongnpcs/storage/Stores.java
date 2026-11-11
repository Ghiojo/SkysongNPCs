package org.skysongdev.skysongnpcs.storage;

import java.util.Objects;

/**
 * The plugin's stores, opened together on one connection pool. The NPC store owns the pool,
 * so it is closed last.
 *
 * @param npcs   the NPC records
 * @param grants the access grants
 * @param policies the access policies (M5)
 * @param groups the player groups (M4)
 * @param items    the NPCs' blueprint items and tickets (M18)
 * @param choices  the NPCs' stat points and setup progress (M14)
 * @param picks    the NPCs' proficiency picks (M15)
 */
public record Stores(NpcStore npcs, GrantStore grants, PolicyStore policies, GroupStore groups,
                     NpcItemStore items, ChoiceStore choices, PickStore picks) {

    public Stores {
        Objects.requireNonNull(npcs, "npcs");
        Objects.requireNonNull(grants, "grants");
        Objects.requireNonNull(policies, "policies");
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(choices, "choices");
        Objects.requireNonNull(picks, "picks");
    }
}
