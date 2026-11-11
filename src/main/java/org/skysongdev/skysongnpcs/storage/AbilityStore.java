package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.abilities.AppliedAbility;

import java.util.List;
import java.util.UUID;

/** Where NPCs' abilities, and what each one did to its NPC, are kept between restarts. */
public interface AbilityStore extends AutoCloseable {

    /** Every saved ability. Called once on enable, on the main thread. */
    List<StoredAbility> loadAll();

    /** Saves an NPC's ability, adding it or replacing what was saved for it. */
    void save(UUID npc, String ability, AppliedAbility applied);

    /** Deletes one of an NPC's abilities, if it is there. */
    void delete(UUID npc, String ability);

    /** Deletes every ability of one NPC. */
    void deleteAll(UUID npc);

    /** Finishes pending writes. */
    @Override
    void close();

    /** An ability, the NPC it is on, and what it did. */
    record StoredAbility(UUID npc, String ability, AppliedAbility applied) {
    }
}
