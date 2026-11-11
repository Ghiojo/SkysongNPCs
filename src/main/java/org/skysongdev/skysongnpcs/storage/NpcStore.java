package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.List;
import java.util.UUID;

/** Where NPC records are kept between restarts. */
public interface NpcStore extends AutoCloseable {

    /** Every saved record. Called once on enable, on the main thread. */
    List<NpcRecord> loadAll();

    /** Saves a record, adding it or replacing the one with the same id. */
    void save(NpcRecord record);

    /** Deletes the record with this id, if there is one. */
    void delete(UUID npc);

    /** Finishes pending writes and lets go of the database. */
    @Override
    void close();
}
