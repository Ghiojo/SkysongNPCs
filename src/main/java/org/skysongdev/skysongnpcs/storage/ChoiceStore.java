package org.skysongdev.skysongnpcs.storage;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where NPCs' choices are kept between restarts (M14): their stat point grants, the points
 * spent from each, and the setup steps they have done.
 */
public interface ChoiceStore extends AutoCloseable {

    /** Every saved point grant, oldest first. Called once on enable, on the main thread. */
    List<StoredPointGrant> loadGrants();

    /** Every saved spend. Called once on enable, on the main thread. */
    List<StoredSpend> loadSpends();

    /** Every setup step NPCs have done. Called once on enable, on the main thread. */
    List<StoredStep> loadSteps();

    /** Saves a grant, adding it or replacing the one with its id. */
    void saveGrant(StoredPointGrant grant);

    void deleteGrant(UUID grant);

    /** Saves what one grant spent on one stat, replacing what was saved; an amount of 0 deletes it. */
    void saveSpend(StoredSpend spend);

    /** Deletes every spend of one NPC. */
    void deleteSpends(UUID npc);

    void saveStep(UUID npc, String step);

    /** Deletes every setup step of one NPC. */
    void deleteSteps(UUID npc);

    /** Deletes everything of one NPC. */
    void deleteAll(UUID npc);

    /** Finishes pending writes. */
    @Override
    void close();

    /**
     * @param created  when it was given, in epoch milliseconds; grants are spent and listed in this order
     * @param eligible stat ids; empty for the NPC's defaults
     */
    record StoredPointGrant(UUID id, UUID npc, String source, int amount, Set<String> eligible, long created) {
        public StoredPointGrant {
            eligible = Set.copyOf(eligible);
        }
    }

    /** Points of one grant spent on one stat. */
    record StoredSpend(UUID npc, UUID grant, String stat, int amount) {
    }

    record StoredStep(UUID npc, String step) {
    }
}
