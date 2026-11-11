package org.skysongdev.skysongnpcs.api.npc;

/** Where a managed NPC is in its life. */
public enum NpcState {
    /** Placed, but its owner has not made its first choices yet (ancestry, points, picks). */
    SETUP,
    /** In the world and ready. NPCs adopted by staff start here. */
    PLACED,
    /** Picked up into an item: kept by Citizens but despawned, until the item places it again. */
    STORED
}
