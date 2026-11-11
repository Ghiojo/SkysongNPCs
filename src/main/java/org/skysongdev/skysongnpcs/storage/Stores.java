package org.skysongdev.skysongnpcs.storage;

import java.util.Objects;

/**
 * The plugin's stores, opened together on one connection pool. The NPC store owns the pool,
 * so it is closed last.
 *
 * @param npcs   the NPC records
 * @param grants the access grants
 */
public record Stores(NpcStore npcs, GrantStore grants) {

    public Stores {
        Objects.requireNonNull(npcs, "npcs");
        Objects.requireNonNull(grants, "grants");
    }
}
