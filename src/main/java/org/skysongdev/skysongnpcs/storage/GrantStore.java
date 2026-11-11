package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;

import java.util.List;
import java.util.UUID;

/** Where NPCs' access grants are kept between restarts. One grant per NPC, source and group. */
public interface GrantStore extends AutoCloseable {

    /** Every saved grant. Called once on enable, on the main thread. */
    List<StoredGrant> loadAll();

    /** Saves a grant, adding it or replacing the one with the same NPC, source and group. */
    void save(UUID npc, AccessGrant grant);

    /** Deletes one grant, if it is there. */
    void delete(UUID npc, GrantSource source, AccessGroup group);

    /** Deletes every grant of one NPC. */
    void deleteAll(UUID npc);

    /** Finishes pending writes. */
    @Override
    void close();

    /** A grant and the NPC it is on. */
    record StoredGrant(UUID npc, AccessGrant grant) {
    }
}
