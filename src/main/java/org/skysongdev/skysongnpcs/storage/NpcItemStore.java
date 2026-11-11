package org.skysongdev.skysongnpcs.storage;

import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.npc.NpcState;

import java.util.Map;
import java.util.UUID;

/**
 * Where each NPC's item data is kept between restarts (M18): the blueprint it was placed
 * from, and while it is picked up, the ticket of the one item that may place it again.
 */
public interface NpcItemStore extends AutoCloseable {

    /** Every saved row by NPC. Called once on enable, on the main thread. */
    Map<UUID, StoredItem> loadAll();

    /** Saves an NPC's row, replacing the one it had. */
    void save(UUID npc, StoredItem item);

    /** Deletes an NPC's row, if it has one. */
    void delete(UUID npc);

    /** Finishes pending writes. */
    @Override
    void close();

    /**
     * One NPC's item data.
     *
     * @param blueprint the blueprint it was placed from, as Paper's item bytes; null when it was not placed from one
     * @param ticket    the id the NPC's current item carries; null before its first pickup
     * @param resume    the state it goes back to when placed again: SETUP or PLACED; null before its first pickup
     */
    record StoredItem(byte @Nullable [] blueprint, @Nullable UUID ticket, @Nullable NpcState resume) {
    }
}
